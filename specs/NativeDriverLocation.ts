import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
    start(endpoint: string, token: string): Promise<void>;
    stop(): Promise<void>;
    getStatus(): Promise<string>;
    getCurrentPosition(): Promise<string>;
}

// Optional so unsupported platforms can load the app without a native lookup crash.
export default TurboModuleRegistry.get<Spec>('NativeDriverLocation');
