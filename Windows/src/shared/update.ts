export interface UpdateState {
  currentVersion: string;
  status: 'idle' | 'checking' | 'current' | 'available' | 'downloading' | 'verifying' | 'paused' | 'ready' | 'installing' | 'error';
  message: string;
  version?: string;
  notes?: string;
  downloadedBytes?: number;
  totalBytes?: number;
  fileName?: string;
  canDownload: boolean;
  routeMode?: 'auto' | 'mirror' | 'direct';
  activeRouteLabel?: string;
  speedBytesPerSecond?: number;
  canResume?: boolean;
  hasCachedDownload?: boolean;
}
