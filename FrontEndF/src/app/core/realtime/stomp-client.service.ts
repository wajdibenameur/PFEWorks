import { Inject, Injectable } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { filter, shareReplay, switchMap } from 'rxjs/operators';
import { APP_CONFIG, AppConfig } from '../config/app-config.token';
import { AUTH_CONTEXT, AuthContextPort } from '../auth/auth-context.port';
import { RealtimeConnectionStore } from './realtime-connection.store';

@Injectable({ providedIn: 'root' })
export class StompClientService {
  private client: Client | null = null;
  private readonly connected$ = new BehaviorSubject<boolean>(false);
  private readonly topicStreams = new Map<string, Observable<unknown>>();
  private isConnecting = false;
  private isConnected = false;
  private reconnectTimeoutId: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempt = 0;
  private manualDisconnect = false;
  private readonly reconnectDelaysMs = [1000, 2000, 5000, 10000];
  private readonly authSubscription: Subscription;

  constructor(
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    @Inject(AUTH_CONTEXT) private readonly authContext: AuthContextPort,
    private readonly connectionStore: RealtimeConnectionStore
  ) {
    this.authSubscription = this.authContext.isAuthenticated$.subscribe((isAuthenticated) => {
      if (!isAuthenticated) {
        this.disconnect();
      }
    });
  }

  subscribe<T>(topic: string): Observable<T> {
    const existing = this.topicStreams.get(topic);
    if (existing) {
      return existing as Observable<T>;
    }

    const sharedStream = new Observable<T>((observer) => {
      this.connect();

      const connectionSubscription = this.connected$
        .pipe(
          filter((connected) => connected),
          switchMap(
            () =>
              new Observable<T>((innerObserver) => {
                if (!this.client) {
                  innerObserver.error(new Error('STOMP client is not connected'));
                  return;
                }

                const subscription: StompSubscription = this.client.subscribe(topic, (message) => {
                  try {
                    innerObserver.next(JSON.parse(message.body) as T);
                  } catch (error) {
                    console.warn('WS PAYLOAD INVALID', topic, error);
                  }
                });

                return () => subscription.unsubscribe();
              })
          )
        )
        .subscribe(observer);

      return () => connectionSubscription.unsubscribe();
    }).pipe(
      shareReplay({ bufferSize: 1, refCount: true })
    );

    this.topicStreams.set(topic, sharedStream as Observable<unknown>);
    return sharedStream;
  }

  reconnect(): void {
    this.manualDisconnect = false;
    this.cleanupConnection(false);
    this.connect();
  }

  publish(destination: string, body: unknown): void {
    this.connect();
    if (!this.client || !this.isConnected) {
      console.warn('WS PUBLISH SKIPPED', { destination, reason: 'not_connected' });
      return;
    }
    const token = this.authContext.getAccessToken();
    if (!token) {
      console.warn('WS PUBLISH SKIPPED', { destination, reason: 'missing_token' });
      return;
    }
    console.debug('WS SEND', { destination, body });
    this.client.publish({
      destination,
      body: JSON.stringify(body),
      headers: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  disconnect(): void {
    this.manualDisconnect = true;
    this.cleanupConnection(true);
  }

  connect(): void {
    // Any explicit connect attempt means we want realtime back online.
    // Reset manual disconnect so unexpected socket closes can auto-reconnect.
    this.manualDisconnect = false;

    if (this.isConnected) {
      console.debug('WS ALREADY CONNECTED');
      return;
    }
    if (this.isConnecting) {
      console.debug('WS ALREADY CONNECTING');
      return;
    }
    if (this.client) {
      return;
    }

    const wsUrl = `${this.config.monitoringApiUrl}/ws`;
    const token = this.authContext.getAccessToken();
    if (!token) {
      this.connectionStore.setError('Authentication token is required for realtime connection');
      return;
    }

    console.debug('WS CONNECT START');
    this.clearReconnectTimer();
    this.isConnecting = true;
    this.isConnected = false;
    this.connectionStore.setConnecting();

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 0
    });
    const currentClient = client;

    client.onConnect = () => {
      if (this.client !== currentClient) {
        return;
      }

      this.isConnecting = false;
      this.isConnected = true;
      this.reconnectAttempt = 0;
      this.connectionStore.setConnected();
      this.connected$.next(true);
      console.debug('WS CONNECTED');
    };

    client.onStompError = (frame) => {
      if (this.client !== currentClient) {
        return;
      }

      const message = frame.headers['message'] ?? 'STOMP error';
      this.isConnecting = false;
      this.isConnected = false;
      this.connectionStore.setError(message);
      this.connected$.next(false);
      console.warn('WS ERROR', message);
      this.client = null;
      this.scheduleReconnect();
    };

    client.onWebSocketError = () => {
      if (this.client !== currentClient) {
        return;
      }

      this.isConnecting = false;
      this.isConnected = false;
      this.connectionStore.setError('WebSocket transport error');
      this.connected$.next(false);
      console.warn('WS ERROR', 'WebSocket transport error');
      this.client = null;
      this.scheduleReconnect();
    };

    client.onWebSocketClose = (event) => {
      if (this.client !== currentClient) {
        return;
      }

      this.isConnecting = false;
      this.isConnected = false;
      this.connectionStore.setDisconnected();
      this.connected$.next(false);
      console.debug('WS CLOSED', { code: event.code, reason: event.reason, wasClean: event.wasClean });
      this.client = null;
      this.scheduleReconnect();
    };

    this.client = client;
    client.activate();
  }

  private scheduleReconnect(): void {
    if (this.manualDisconnect) {
      return;
    }
    if (!this.authContext.getAccessToken()) {
      return;
    }
    if (this.reconnectTimeoutId) {
      return;
    }

    const baseDelay = this.reconnectDelaysMs[Math.min(this.reconnectAttempt, this.reconnectDelaysMs.length - 1)];
    const jitter = Math.floor(Math.random() * 500);
    const delay = baseDelay + jitter;
    this.reconnectAttempt = Math.min(this.reconnectAttempt + 1, this.reconnectDelaysMs.length - 1);
    console.debug('WS RECONNECT SCHEDULED', delay);
    this.reconnectTimeoutId = setTimeout(() => {
      this.reconnectTimeoutId = null;
      this.connect();
    }, delay);
  }

  private cleanupConnection(clearTopics: boolean): void {
    if (clearTopics) {
      this.topicStreams.clear();
    }
    this.clearReconnectTimer();
    this.isConnecting = false;
    this.isConnected = false;
    this.connected$.next(false);
    this.connectionStore.setDisconnected();

    if (this.client) {
      const currentClient = this.client;
      this.client = null;
      void currentClient.deactivate();
    }
    console.debug('WS CLEANUP DONE');
  }

  private clearReconnectTimer(): void {
    if (!this.reconnectTimeoutId) {
      return;
    }
    clearTimeout(this.reconnectTimeoutId);
    this.reconnectTimeoutId = null;
  }
}
