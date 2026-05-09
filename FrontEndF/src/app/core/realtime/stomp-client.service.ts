import { Inject, Injectable } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { BehaviorSubject, Observable } from 'rxjs';
import { filter, shareReplay, switchMap } from 'rxjs/operators';
import { APP_CONFIG, AppConfig } from '../config/app-config.token';
import { AUTH_CONTEXT, AuthContextPort } from '../auth/auth-context.port';
import { RealtimeConnectionStore } from './realtime-connection.store';

@Injectable({ providedIn: 'root' })
export class StompClientService {
  private client: Client | null = null;
  private readonly connected$ = new BehaviorSubject<boolean>(false);
  private readonly topicStreams = new Map<string, Observable<unknown>>();
  private reconnecting = false;

  constructor(
    @Inject(APP_CONFIG) private readonly config: AppConfig,
    @Inject(AUTH_CONTEXT) private readonly authContext: AuthContextPort,
    private readonly connectionStore: RealtimeConnectionStore
  ) {}

  subscribe<T>(topic: string): Observable<T> {
    const existing = this.topicStreams.get(topic);
    if (existing) {
      return existing as Observable<T>;
    }

    const sharedStream = new Observable<T>((observer) => {
      this.ensureConnected();

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
                    innerObserver.error(error);
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
    if (this.reconnecting || this.connectionStore.status() === 'CONNECTING') {
      return;
    }

    this.disconnect();
    this.reconnecting = true;
    this.ensureConnected();
  }

  disconnect(): void {
    this.topicStreams.clear();

    if (!this.client) {
      this.reconnecting = false;
      this.connected$.next(false);
      this.connectionStore.setDisconnected();
      return;
    }

    const currentClient = this.client;
    this.client = null;
    this.connected$.next(false);
    this.connectionStore.setDisconnected();
    void currentClient.deactivate();
  }

  private ensureConnected(): void {
    if (this.client) {
      return;
    }

    const wsUrl = `${this.config.monitoringApiUrl}/ws`;
    const token = this.authContext.getAccessToken();
    if (!token) {
      this.reconnecting = false;
      this.connectionStore.setError('Authentication token is required for realtime connection');
      return;
    }

    this.connectionStore.setConnecting();

    const client = new Client({
      webSocketFactory: () => new SockJS(wsUrl),
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      reconnectDelay: 5000
    });
    const currentClient = client;

    client.onConnect = () => {
      if (this.client !== currentClient) {
        return;
      }

      this.reconnecting = false;
      this.connectionStore.setConnected();
      this.connected$.next(true);
    };

    client.onStompError = (frame) => {
      if (this.client !== currentClient) {
        return;
      }

      const message = frame.headers['message'] ?? 'STOMP error';
      this.reconnecting = false;
      this.connectionStore.setError(message);
      this.connected$.next(false);
      this.client = null;
    };

    client.onWebSocketError = () => {
      if (this.client !== currentClient) {
        return;
      }

      this.connectionStore.setError('WebSocket transport error');
      this.connected$.next(false);
    };

    client.onWebSocketClose = () => {
      if (this.client !== currentClient) {
        return;
      }

      this.reconnecting = false;
      this.connectionStore.setDisconnected();
      this.connected$.next(false);
      this.client = null;
    };

    client.activate();
    this.client = client;
  }
}
