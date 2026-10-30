export interface UpdateState {
  currentVersion: string;
  status: 'idle' | 'checking' | 'current' | 'available' | 'downloading' | 'ready' | 'error';
  message: string;
  version?: string;
  notes?: string;
  downloadedBytes?: number;
  totalBytes?: number;
  fileName?: string;
  canDownload: boolean;
}
