import React, { createContext, useState, useEffect, useCallback, useContext, useMemo } from 'react';
import { AppState, Platform } from 'react-native';
import { check, PERMISSIONS, RESULTS } from 'react-native-permissions';
import { Place, Point } from '@fleetbase/sdk';
import { useAuth } from './AuthContext';
import useStorage from '../hooks/use-storage';
import useFleetbase from '../hooks/use-fleetbase';
import { buildTrackingEndpoint, driverLocation, type DriverPosition, type TrackingStatus } from '../services/driver-location';

const LocationContext = createContext<any>(undefined);
export const isLocationTrackingAllowed = ({ driver, isOnline, permissionStatus, authToken }) => Boolean(driver && isOnline && authToken && permissionStatus === RESULTS.GRANTED);

export const LocationProvider = ({ children }) => {
    const { isOnline, driver } = useAuth();
    const { adapter } = useFleetbase();
    const [authToken] = useStorage<string>('_driver_token');
    const [location, setLocation] = useState<DriverPosition | null>(null);
    const [status, setStatus] = useState<TrackingStatus | null>(null);
    const [permissionStatus, setPermissionStatus] = useState<string>(RESULTS.DENIED);
    const [trackingError, setTrackingError] = useState<string | null>(null);
    const canTrack = Platform.OS === 'android' && isLocationTrackingAllowed({ driver, isOnline, permissionStatus, authToken });
    const endpoint = adapter?.host && adapter?.namespace && driver?.id ? buildTrackingEndpoint(adapter.host, adapter.namespace, driver.id) : null;

    useEffect(() => {
        let mounted = true;
        const refresh = async () => {
            if (Platform.OS !== 'android') return;
            try {
                const result = await check(PERMISSIONS.ANDROID.ACCESS_FINE_LOCATION);
                if (mounted) setPermissionStatus(result);
            } catch {
                if (mounted) setPermissionStatus(RESULTS.DENIED);
            }
        };
        void refresh();
        const subscription = AppState.addEventListener('change', (state) => {
            if (state === 'active') void refresh();
        });
        return () => {
            mounted = false;
            subscription.remove();
        };
    }, []);

    const stopTracking = useCallback(async () => {
        await driverLocation.stop();
        setStatus(null);
        setLocation(null);
    }, []);

    const startTracking = useCallback(async () => {
        if (!canTrack || !endpoint || !authToken) return;
        try {
            await driverLocation.start(endpoint, authToken);
            setTrackingError(null);
        } catch {
            setTrackingError('start_rejected');
        }
    }, [canTrack, endpoint, authToken]);

    useEffect(() => {
        let mounted = true;
        setLocation(null);
        setStatus(null);
        setTrackingError(null);
        if (!canTrack || !endpoint) {
            void driverLocation.stop().catch(() => {
                if (mounted) setTrackingError('stop_failed');
            });
            return () => {
                mounted = false;
            };
        }
        void startTracking();
        let reading = false;
        const refresh = async () => {
            if (reading || AppState.currentState !== 'active') return;
            reading = true;
            try {
                const next = await driverLocation.getStatus();
                if (mounted) {
                    setStatus(next);
                    setLocation(next.location);
                    setTrackingError(next.error);
                }
            } catch {
                if (mounted) setTrackingError('status_unavailable');
            } finally {
                reading = false;
            }
        };
        // UI refresh only. The native service owns acquisition and HTTP with JS suspended.
        const timer = setInterval(refresh, 2_000);
        void refresh();
        return () => {
            mounted = false;
            clearInterval(timer);
            void driverLocation.stop().catch(() => {});
        };
    }, [canTrack, endpoint, authToken, startTracking]);

    const trackLocation = useCallback(async () => {
        if (!canTrack) return;
        const point = await driverLocation.getCurrentPosition();
        setLocation(point);
        return point; // Display only; native service is the sole tracking transport.
    }, [canTrack]);

    const getDriverLocationAsPlace = useCallback(
        (attributes = {}) => {
            if (!location?.coords) return null;
            return new Place(
                { id: 'driver', name: 'Driver Location', street1: 'Driver Location', location: new Point(location.coords.latitude, location.coords.longitude), ...attributes },
                adapter
            );
        },
        [location, adapter]
    );

    const value = useMemo(
        () => ({
            location,
            isTracking: status?.status === 'tracking',
            trackingError,
            deliveryStatus: status?.delivery ?? 'idle',
            lastSentAt: status?.lastSentAt,
            startTracking,
            stopTracking,
            getDriverLocationAsPlace,
            trackLocation,
        }),
        [location, status, trackingError, startTracking, stopTracking, getDriverLocationAsPlace, trackLocation]
    );
    return <LocationContext.Provider value={value}>{children}</LocationContext.Provider>;
};

export const useLocation = () => {
    const context = useContext(LocationContext);
    if (context === undefined) throw new Error('useLocation must be used within a LocationProvider');
    return context;
};
