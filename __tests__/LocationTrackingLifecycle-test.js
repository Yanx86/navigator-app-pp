import React from 'react';
import ReactTestRenderer from 'react-test-renderer';
import { AppState, View } from 'react-native';
import { check, RESULTS } from 'react-native-permissions';
import { LocationProvider, isLocationTrackingAllowed, useLocation } from '../src/contexts/LocationContext';
import { driverLocation } from '../src/services/driver-location';

let mockAuthState;
let mockToken;
let mockContext;
let renderer;
const mockAdapter = { host: 'https://example.test', namespace: 'fleet-ops/v1' };
jest.mock('react-native-permissions', () => ({
    check: jest.fn(),
    PERMISSIONS: { ANDROID: { ACCESS_FINE_LOCATION: 'fine' } },
    RESULTS: { DENIED: 'denied', GRANTED: 'granted' },
}));
jest.mock('../src/services/driver-location', () => ({
    buildTrackingEndpoint: (host, namespace, id) => `${host}/${namespace}/drivers/${id}/track`,
    driverLocation: { start: jest.fn(), stop: jest.fn(), getStatus: jest.fn(), getCurrentPosition: jest.fn() },
}));
jest.mock('@fleetbase/sdk', () => ({ Place: class {}, Point: class {} }));
jest.mock('../src/contexts/AuthContext', () => ({ useAuth: () => mockAuthState }));
jest.mock('../src/hooks/use-storage', () => () => [mockToken, jest.fn()]);
jest.mock('../src/hooks/use-fleetbase', () => () => ({ adapter: mockAdapter }));
const Probe = () => {
    mockContext = useLocation();
    return <View />;
};
const app = () => (
    <LocationProvider>
        <Probe />
    </LocationProvider>
);
const mount = async () =>
    ReactTestRenderer.act(async () => {
        renderer = ReactTestRenderer.create(app());
    });

beforeEach(() => {
    jest.useFakeTimers();
    jest.clearAllMocks();
    Object.defineProperty(require('react-native').Platform, 'OS', { configurable: true, value: 'android' });
    AppState.currentState = 'active';
    mockAuthState = { driver: { id: 'a' }, isOnline: true };
    mockToken = 'test-session';
    check.mockResolvedValue(RESULTS.GRANTED);
    driverLocation.start.mockResolvedValue(undefined);
    driverLocation.stop.mockResolvedValue(undefined);
    driverLocation.getStatus.mockResolvedValue({ status: 'tracking', error: null, delivery: 'sent', location: null, lastSentAt: 1 });
});
afterEach(async () => {
    if (renderer) await ReactTestRenderer.act(async () => renderer.unmount());
    renderer = null;
    jest.useRealTimers();
});

test('requires chauffeur, online state, precise permission and token', () => {
    const args = { driver: {}, isOnline: true, permissionStatus: RESULTS.GRANTED, authToken: 'token' };
    expect(isLocationTrackingAllowed(args)).toBe(true);
    for (const override of [{ driver: null }, { isOnline: false }, { permissionStatus: RESULTS.DENIED }, { authToken: '' }]) {
        expect(isLocationTrackingAllowed({ ...args, ...override })).toBe(false);
    }
});

test('starts one native transport for the current chauffeur', async () => {
    await mount();
    expect(driverLocation.start).toHaveBeenCalledTimes(1);
    expect(driverLocation.start).toHaveBeenCalledWith('https://example.test/fleet-ops/v1/drivers/a/track', 'test-session');
    expect(mockContext.isTracking).toBe(true);
});

test.each(['permission', 'token'])('does not start without %s', async (reason) => {
    if (reason === 'permission') check.mockResolvedValue(RESULTS.DENIED);
    else mockToken = null;
    await mount();
    expect(driverLocation.start).not.toHaveBeenCalled();
    expect(driverLocation.stop).toHaveBeenCalled();
});

test('going offline stops native acquisition', async () => {
    await mount();
    driverLocation.stop.mockClear();
    mockAuthState = { ...mockAuthState, isOnline: false };
    await ReactTestRenderer.act(async () => renderer.update(app()));
    expect(driverLocation.stop).toHaveBeenCalled();
    expect(mockContext.isTracking).toBe(false);
});

test('session replacement stops before starting the next chauffeur', async () => {
    await mount();
    driverLocation.stop.mockClear();
    driverLocation.start.mockClear();
    mockAuthState = { driver: { id: 'b' }, isOnline: true };
    mockToken = 'other-session';
    await ReactTestRenderer.act(async () => renderer.update(app()));
    expect(driverLocation.start).toHaveBeenCalledWith('https://example.test/fleet-ops/v1/drivers/b/track', 'other-session');
    expect(driverLocation.stop.mock.invocationCallOrder[0]).toBeLessThan(driverLocation.start.mock.invocationCallOrder[0]);
});

test('a terminal native error is displayed without automatically restarting', async () => {
    await mount();
    driverLocation.getStatus.mockResolvedValue({ status: 'error', error: 'session_expired', delivery: 'idle', location: null });
    await ReactTestRenderer.act(async () => jest.advanceTimersByTime(2000));
    expect(mockContext.isTracking).toBe(false);
    expect(mockContext.trackingError).toBe('session_expired');
    expect(driverLocation.start).toHaveBeenCalledTimes(1);
});

test('suspending JS UI polling does not stop the native service', async () => {
    await mount();
    driverLocation.stop.mockClear();
    driverLocation.getStatus.mockClear();
    AppState.currentState = 'background';
    await ReactTestRenderer.act(async () => jest.advanceTimersByTime(4000));
    expect(driverLocation.stop).not.toHaveBeenCalled();
    expect(driverLocation.getStatus).not.toHaveBeenCalled();
});
