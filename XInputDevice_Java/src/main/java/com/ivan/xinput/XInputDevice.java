package com.ivan.xinput;

import static com.ivan.xinput.natives.XInputConstants.ERROR_DEVICE_NOT_CONNECTED;
import static com.ivan.xinput.natives.XInputConstants.ERROR_SUCCESS;
import static com.ivan.xinput.natives.XInputConstants.MAX_PLAYERS;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_A;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_B;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_BACK;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_DPAD_DOWN;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_DPAD_LEFT;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_DPAD_RIGHT;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_DPAD_UP;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_LEFT_SHOULDER;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_LEFT_THUMB;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_RIGHT_SHOULDER;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_RIGHT_THUMB;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_START;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_X;
import static com.ivan.xinput.natives.XInputConstants.XINPUT_GAMEPAD_Y;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;

import com.ivan.xinput.enums.XInputButton;
import com.ivan.xinput.exceptions.XInputNotLoadedException;
import com.ivan.xinput.listener.XInputDeviceListener;
import com.ivan.xinput.natives.XInputNatives;

/**
 * Represents all XInput devices registered in the system.
 * Use the {@link #getAllDevices()} or {@link #getDeviceFor(int)} methods to start using the devices.
 *
 * @author Ivan "StrikerX3" Oliveira
 * @see XInputComponents
 * @see XInputComponentsDelta
 */
public class XInputDevice {
    protected final int playerNum;
    private final ByteBuffer buffer; // Contains the XINPUT_STATE struct
    private final XInputComponents lastComponents;
    private final XInputComponents components;
    private final XInputComponentsDelta delta;

    private boolean lastConnected;
    private boolean connected;

    private final List<XInputDeviceListener> listeners;

    private static final XInputDevice[] DEVICES;
    static {
        XInputDevice[] devices;
        if (XInputNatives.isLoaded()) {
            devices = new XInputDevice[MAX_PLAYERS];
            for (int i = 0; i < MAX_PLAYERS; i++) {
                devices[i] = new XInputDevice(i);
            }
        } else {
            devices = null;
        }
        DEVICES = devices;
    }

    protected XInputDevice(final int playerNum) {
        this.playerNum = playerNum;
        buffer = ByteBuffer.allocateDirect(16); // sizeof(XINPUT_STATE)
        buffer.order(ByteOrder.nativeOrder());

        lastComponents = new XInputComponents();
        components = new XInputComponents();
        delta = new XInputComponentsDelta(lastComponents, components);

        listeners = new LinkedList<XInputDeviceListener>();

        poll();
    }

    /**
     * Determines if the XInput devices are available on this platform.
     *
     * @return <code>true</code> if the XInput devices are available, <code>false</code> if not
     */
    public static boolean isAvailable() {
        return DEVICES != null;
    }

    /**
     * Returns an array containing all registered XInput devices.
     *
     * @return all XInput devices
     * @throws XInputNotLoadedException if the native library failed to load
     */
    public static XInputDevice[] getAllDevices() throws XInputNotLoadedException {
        checkLibraryReady();
        return DEVICES.clone();
    }

    /**
     * Returns the XInput device for the specified player.
     *
     * @param playerNum the player number
     * @return the XInput device for the specified player
     * @throws XInputNotLoadedException if the native library failed to load
     */
    public static XInputDevice getDeviceFor(final int playerNum) throws XInputNotLoadedException {
        checkLibraryReady();
        if (playerNum < 0 || playerNum >= MAX_PLAYERS) {
            throw new IllegalArgumentException("Invalid player number: " + playerNum + ". Must be between 0 and " + (MAX_PLAYERS - 1));
        }
        return DEVICES[playerNum];
    }

    /**
     * Adds an event listener that will react to changes in the input.
     *
     * @param listener the listener
     */
    public void addListener(final XInputDeviceListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a registered event listener
     *
     * @param listener the listener
     */
    public void removeListener(final XInputDeviceListener listener) {
        listeners.remove(listener);
    }

    /**
     * Reads input from the device and updates components.
     *
     * @return <code>false</code> if the device is not connected
     * @throws IllegalStateException if there is an error trying to read the device state
     */
    public boolean poll() {
        if (!checkReturnCode(XInputNatives.pollDevice(playerNum, buffer))) {
            return false;
        }
        setConnected(true);

        // typedef struct _XINPUT_STATE
        // {
        //     DWORD                               dwPacketNumber;
        //     XINPUT_GAMEPAD                      Gamepad;
        // } XINPUT_STATE, *PXINPUT_STATE;

        // typedef struct _XINPUT_GAMEPAD
        // {
        //     WORD                                wButtons;
        //     BYTE                                bLeftTrigger;
        //     BYTE                                bRightTrigger;
        //     SHORT                               sThumbLX;
        //     SHORT                               sThumbLY;
        //     SHORT                               sThumbRX;
        //     SHORT                               sThumbRY;
        // } XINPUT_GAMEPAD, *PXINPUT_GAMEPAD;

        /*int packetNumber = */buffer.getInt(); // can be safely ignored
        final short btns = buffer.getShort();
        final byte leftTrigger = buffer.get();
        final byte rightTrigger = buffer.get();
        final short thumbLX = buffer.getShort();
        final short thumbLY = buffer.getShort();
        final short thumbRX = buffer.getShort();
        final short thumbRY = buffer.getShort();
        buffer.flip();

        lastComponents.copy(components);

        final boolean up = (btns & XINPUT_GAMEPAD_DPAD_UP) != 0;
        final boolean down = (btns & XINPUT_GAMEPAD_DPAD_DOWN) != 0;
        final boolean left = (btns & XINPUT_GAMEPAD_DPAD_LEFT) != 0;
        final boolean right = (btns & XINPUT_GAMEPAD_DPAD_RIGHT) != 0;

        final XInputAxes axes = components.getAxes();
        axes.lx = thumbLX / 32768f;
        axes.ly = thumbLY / 32768f;
        axes.rx = thumbRX / 32768f;
        axes.ry = thumbRY / 32768f;
        axes.lt = (leftTrigger & 0xff) / 255f;
        axes.rt = (rightTrigger & 0xff) / 255f;
        axes.dpad = XInputAxes.dpadFromButtons(up, down, left, right);

        final XInputButtons buttons = components.getButtons();
        buttons.a = (btns & XINPUT_GAMEPAD_A) != 0;
        buttons.b = (btns & XINPUT_GAMEPAD_B) != 0;
        buttons.x = (btns & XINPUT_GAMEPAD_X) != 0;
        buttons.y = (btns & XINPUT_GAMEPAD_Y) != 0;
        buttons.back = (btns & XINPUT_GAMEPAD_BACK) != 0;
        buttons.start = (btns & XINPUT_GAMEPAD_START) != 0;
        buttons.lShoulder = (btns & XINPUT_GAMEPAD_LEFT_SHOULDER) != 0;
        buttons.rShoulder = (btns & XINPUT_GAMEPAD_RIGHT_SHOULDER) != 0;
        buttons.lThumb = (btns & XINPUT_GAMEPAD_LEFT_THUMB) != 0;
        buttons.rThumb = (btns & XINPUT_GAMEPAD_RIGHT_THUMB) != 0;
        buttons.up = up;
        buttons.down = down;
        buttons.left = left;
        buttons.right = right;

        processDelta();
        return true;
    }

    protected boolean checkReturnCode(final int ret) {
        if (ret == ERROR_DEVICE_NOT_CONNECTED) {
            setConnected(false);
            return false;
        }
        if (ret != ERROR_SUCCESS) {
            setConnected(false);
            throw new IllegalStateException("Could not read controller state: 0x" + Integer.toHexString(ret));
        }
        return true;
    }

    protected boolean checkReturnCode(final int ret, final int... validRetCodes) {
        if (ret == ERROR_DEVICE_NOT_CONNECTED) {
            setConnected(false);
            return false;
        }
        if (ret != ERROR_SUCCESS) {
            setConnected(false);
            for (final int validRet : validRetCodes) {
                if (ret == validRet) {
                    return false;
                }
            }
            throw new IllegalStateException("Could not read controller state: 0x" + Integer.toHexString(ret));
        }
        return true;
    }

    private void setConnected(final boolean state) {
        lastConnected = connected;
        connected = state;
        for (final XInputDeviceListener listener : listeners) {
            if (connected && !lastConnected) {
                listener.connected();
            } else if (!connected && lastConnected) {
                listener.disconnected();
            }
        }
    }

    private void processDelta() {
        final XInputButtonsDelta buttons = delta.getButtons();
        for (final XInputDeviceListener listener : listeners) {
            for (final XInputButton button : XInputButton.values()) {
                if (buttons.isPressed(button)) {
                    listener.buttonChanged(button, true);
                } else if (buttons.isReleased(button)) {
                    listener.buttonChanged(button, false);
                }
            }
        }
    }

    /**
     * Sets the vibration of the controller. Returns <code>false</code> if the device was not connected.
     *
     * @param leftMotor the left motor speed, from 0 to 65535
     * @param rightMotor the right motor speed, from 0 to 65535
     * @return <code>false</code> if the device was not connected
     * @throws IllegalArgumentException if either motor speed values lie out of the range 0..65535
     */
    public boolean setVibration(final int leftMotor, final int rightMotor) {
        if (leftMotor < 0 || leftMotor > 65535) {
            throw new IllegalArgumentException("Left motor speed out of range (0..65535): " + leftMotor);
        }
        if (rightMotor < 0 || rightMotor > 65535) {
            throw new IllegalArgumentException("Right motor speed out of range (0..65535): " + rightMotor);
        }
        return XInputNatives.setVibration(playerNum, leftMotor, rightMotor) == ERROR_SUCCESS;
    }

    /**
     * Returns the state of the XInput controller components before the last poll.
     *
     * @return the state of the XInput controller components before the last poll.
     */
    public XInputComponents getLastComponents() {
        return lastComponents;
    }

    /**
     * Returns the state of the XInput controller components at the last poll.
     *
     * @return the state of the XInput controller components at the last poll.
     */
    public XInputComponents getComponents() {
        return components;
    }

    /**
     * Returns the difference between the last two states of the XInput controller components.
     *
     * @return the difference between the last two states of the XInput controller components.
     */
    public XInputComponentsDelta getDelta() {
        return delta;
    }

    /**
     * Returns a boolean indicating whether this device is connected.
     *
     * @return <code>true</code> if the device is connected, <code>false</code> otherwise
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Returns the player number that this device represents.
     *
     * @return the player number that this device represents.
     */
    public int getPlayerNum() {
        return playerNum;
    }

    /**
     * Checks if the native library is loaded and ready for use.
     *
     * @throws XInputNotLoadedException if the native library is not loaded
     */
    private static void checkLibraryReady() throws XInputNotLoadedException {
        if (!XInputNatives.isLoaded()) {
            throw new XInputNotLoadedException("Native library failed to load", XInputNatives.getLoadError());
        }
    }
}
