import { Platform } from 'react-native';
import NativeDriverLocation from '../../specs/NativeDriverLocation';

export type DriverPosition = {
    timestamp: number;
    coords: { latitude: number; longitude: number; accuracy: number; altitude: number | null; heading: number | null; speed: number | null };
};
export type TrackingStatus = {
    status: 'stopped' | 'starting' | 'tracking' | 'error';
    error: string | null;
    delivery: 'idle' | 'sent' | 'unavailable';
    lastSentAt: number | null;
    location: DriverPosition | null;
};

const nativeModule = () => {
    if (Platform.OS !== 'android' || !NativeDriverLocation) throw new Error('Native Android location module unavailable.');
    return NativeDriverLocation;
};

export const buildTrackingEndpoint = (host: string, namespace: string, driverId: string) =>
    `${host.replace(/\/+$/, '')}/${namespace.replace(/^\/+|\/+$/g, '')}/drivers/${encodeURIComponent(driverId)}/track`;

export const driverLocation = {
    start: (endpoint: string, token: string) => nativeModule().start(endpoint, token),
    stop: async () => {
        if (Platform.OS === 'android' && NativeDriverLocation) await NativeDriverLocation.stop();
    },
    getStatus: async (): Promise<TrackingStatus> => JSON.parse(await nativeModule().getStatus()),
    getCurrentPosition: async (): Promise<DriverPosition> => JSON.parse(await nativeModule().getCurrentPosition()),
};
