import { CameraDevice } from '../../../core/models/camera-device.model';
import { RealtimeDataState } from './global-monitoring.models';

export interface CameraRealtimeViewModel {
  camera: CameraDevice;
  realtimeState: RealtimeDataState;
  lastMetricUpdate?: number;
}

export interface RealtimeEntityStore<T> {
  entities: Map<string, T>;
  realtimeState: RealtimeDataState;
  lastUpdate?: number;
}

