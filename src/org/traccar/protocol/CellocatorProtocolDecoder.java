/*
 * Copyright 2013 - 2018 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.NetworkMessage;
import org.traccar.helper.DateBuilder;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;

import java.net.SocketAddress;

public class CellocatorProtocolDecoder extends BaseProtocolDecoder {

    public CellocatorProtocolDecoder(CellocatorProtocol protocol) {
        super(protocol);
    }

    static final int MSG_CLIENT_STATUS = 0;
    static final int MSG_CLIENT_PROGRAMMING = 3;
    static final int MSG_CLIENT_SERIAL_LOG = 7;
    static final int MSG_CLIENT_SERIAL = 8;
    static final int MSG_CLIENT_MODULAR = 9;
    private static final double CELLO_TRACK_BATTERY_CONST = 0.01953125;
    private static final double COMPACT_SECURITY_BATTERY_CONST = 0.1217320;
    private double batteryConst;

    public static final int MSG_SERVER_ACKNOWLEDGE = 4;

    private byte commandCount;

    private void sendReply(Channel channel, SocketAddress remoteAddress, long deviceId, byte packetNumber) {
        if (channel != null) {
            ByteBuf reply = Unpooled.buffer(28);
            reply.writeByte('M');
            reply.writeByte('C');
            reply.writeByte('G');
            reply.writeByte('P');
            reply.writeByte(MSG_SERVER_ACKNOWLEDGE);
            reply.writeIntLE((int) deviceId);
            reply.writeByte(commandCount++);
            reply.writeIntLE(0); // authentication code
            reply.writeByte(0);
            reply.writeByte(packetNumber);
            reply.writeZero(11);

            byte checksum = 0;
            for (int i = 4; i < 27; i++) {
                checksum += reply.getByte(i);
            }
            reply.writeByte(checksum);

            channel.writeAndFlush(new NetworkMessage(reply, remoteAddress));
        }
    }

    private String decodeAlarm(short reason) {
        switch (reason) {
            case 4:
                return "Emergency mode by command";
            case 5:
                return "Door opened";
            case 6:
                return "Engine activated";
            case 7:
                return "Gps disconnected";
            case 8:
                return "Location change detected on Ignition is Off";
            case 11:
                return "Communication Idle";
            case 12:
                return "Disarmed from emergency states";
            case 13:
                return "Keypad Locked (wrong codes punched in)";
            case 14:
                return "Garage Mode";
            case 19:
                return "Alarm Triggered by \"Lock\" input";
            case 21:
                return "Coasting detection (Speed and RPM)";
            case 22:
                return "Violation of 1st additional GP frequency threshold";
            case 23:
                return "Violation of 2nd additional GP frequency threshold";
            case 25:
                return "Speed detected when Ignition is Off";
            case 27:
                return "GPS connected";
            case 31:
                return "Reply to Command";
            case 32:
                return "IP changed / connection up";
            case 33:
                return "GPS Navigation Start";
            case 34:
                return "Over-speed Start";
            case 35:
                return "Idle Speed Start";
            case 36:
                return "Distance";
            case 37:
                return "Engine Start; Ignition Input – active (high)";
            case 38:
                return "GPS Factory Reset (Automatic only)";
            case 40:
                return "IP Down";
            case 41:
                return "GPS Navigation End";
            case 42:
                return "End of Over-speed";
            case 43:
                return "End of Idle Speed";
            case 44:
                return "Timed Event";
            case 45:
                return "Engine Stop; Ignition Input – inactive (low)";
            case 46:
                return "Driver Authentication Update / Code received for Cello-AR";
            case 47:
                return "Driving Without Authentication";
            case 48:
                return "Door Close Event";
            case 49:
                return "CelloTrack: GP1 Inactive Event"; // Unlock2 / Shock Inactive Event
            case 50:
                return "CelloTrack: GP2 Inactive Event"; // Hood Sensor Inactive Event
            case 51:
                return "Volume Sensor Inactive Event";
            case 52:
                return "Hotwire Sensor Inactive Event";
            case 53:
                return "Driving Stop Event";
            case 54:
                return "Distress Button Inactive Event";
            case 55:
                return "Unlock Input Inactive event";
            case 56:
                return "Oil Pressure Sensor Inactive Event";
            case 57:
                return "CFE input 1 inactive (Infrustructure)";
            case 58:
                return "Lock input inactive event";
            case 59:
                return "CFE input 2 inactive (Infrustructure)";
            case 60:
                return "CFE input 3 inactive (Infrustructure)";
            case 61:
                return "CFE input 4 inactive (Infrustructure)";
            case 62:
                return "CFE input 5 inactive (Infrustructure)";
            case 63:
                return "CFE input 6 inactive (Infrustructure)";
            case 64 :
                return "Door Open Event";
            case 65:
                return "CelloTrack: GP1 Active Event"; // Unlock2 / Shock Active Event
            case 66:
                return "CelloTrack: GP2 Active Event"; // Hood Sensor Active Event
            case 67:
                return "Volume Sensor Active Event";
            case 68:
                return "Hotwire Sensor Active Event (370-50)";
            case 69:
                return "Driving Start Event";
            case 70:
                return "Distress Button Active Event";
            // return Position.ALARM_SOS;
            case 71:
                return "Unlock Input Active Event";
            case 72:
                return "Oil Pressure Sensor Active Event";
            case 73:
                return "CFE input 1 active Event (Infrustructure)";
            case 74:
                return "Lock input active event";
            case 75:
                return "CFE input 2 active Event (Infrustructure)";
            case 76:
                return "CFE input 3 active Event (Infrustructure)";
            case 77:
                return "CFE input 4 active Event (Infrustructure)";
            case 78:
                return "CFE input 5 active Event (Infrustructure)";
            case 79:
                return "CFE input 6 active Event (Infrustructure)";
            case 80:
                return "Main Power Disconnected";
            // return Position.ALARM_POWER_CUT;
            case 81:
                return "Main Power Low Level";
            // return Position.ALARM_LOW_POWER;
            case 82:
                return "Backup Battery Disconnected"; // Cellotrack3G: Charging Power Disconnected
            case 83:
                return "Backup Battery Low Level";
            case 84:
                return "Halt (movement end) event";
            case 85:
                return "Go (movement start) event";
            case 87:
                return "Main Power Connected (be unconditionally logged upon an initial power up)";
            case 88:
                return "Main Power High Level";
            case 89:
                return "Backup Battery Connected"; // Cellotrack3G Power: Charging Power Connected
            case 90:
                return "Backup Battery High Level";
            case 91:
                return "Message from SPC Keyboard";
            case 99:
                return "Harsh Braking Sensor Triggered";
            case 100:
                return "Sudden Course Change Sensor Triggered";
            case 101:
                return "Harsh Acceleration Sensor Triggered";
            case 104:
                return "Trigger on General Input";
            case 105:
                return "Arm Input triggered";
            case 106:
                return "Disarm Input triggered";
            case 107:
                return "Remote Controller input trigger";
            case 108:
                return "Odometer pulse received";
            case 109:
                return "Unlock Pulse trigger";
            case 110:
                return "Lock Pulse trigger";
            case 111:
                return "Triggers on Blinkers";
            case 112:
                return "One of the protected outputs failure";
            case 144:
                return "Reset while armed";
            case 145:
                return "Wireless Panic button (for RB modification only)";
            case 150:
                return "Signal Learned";
            case 151:
                return "Learning Failed";
            case 152:
                return "Received Signal A";
            case 153:
                return "Received Signal B";
            case 154:
                return "Car sharing";
            case 158:
                return "Tamper switch Active Event (For CelloTrack only)";
            case 159:
                return "Tamper switch Inactive Event (For CelloTrack only)";
            case 161:
                return "\"Unlock\" input triggered";
            case 162:
                return "MODECON gas leak start event";
            case 163:
                return "MODECON gas leak stop event";
            case 190:
                return "No Modem Zone entry";
            case 191:
                return "Geo-HOT Spot violation";
            case 192:
                return "Frequency Measurement Threshold Violation";
            case 194:
                return "Analog Measurement Threshold Violation";
            case 199:
                return "Trailer Connection Status";
            case 200:
                return "Modem's Auto Hardware Reset (AHR)";
            case 201:
                return "PSP – External Alarm is Triggered";
            case 202:
                return "Wake Up event";
            case 203:
                return "Pre-Hibernation event";
            case 204:
                return "Vector (course) change";
            case 205:
                return "Garmin connection status changed";
            case 206:
                return "Jamming detection";
            case 207:
                return "Radio Off Mode";
            case 208:
                return "Header Error";
            case 209:
                return "Script Version Error";
            case 210:
                return "Unsupported Command";
            case 211:
                return "Bad Parameters";
            case 212:
                return "Speed limiting GeoFence - over Speed Start Event";
            case 213:
                return "Speed limiting GeoFence - over Speed End Event";
            case 232:
                return "External EEPROM Error";
            case 239:
                return "Max Error";
            case 245:
                return "Upload Mode";
            case 246:
                return "Execute Mode";
            case 247:
                return "Finish Mode";
            case 248:
                return "Post Boot Mode";
            case 252:
                return "COM-Location Glancing";
            case 253:
                return "Violation of Keep In Fence";
            case 254:
                return "Violation of Keep Out Fence";
            case 255:
                return "Violation of Way Point";
            default:
                return "Unrecognised cause";
        }
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        ByteBuf buf = (ByteBuf) msg;

        buf.skipBytes(4); // system code
        int type = buf.readUnsignedByte();
        long deviceUniqueId = buf.readUnsignedIntLE();

        if (type != MSG_CLIENT_SERIAL) {
            buf.readUnsignedShortLE(); // communication control
        }
        byte packetNumber = buf.readByte(); // Message numerator

        sendReply(channel, remoteAddress, deviceUniqueId, packetNumber);

        if (type == MSG_CLIENT_STATUS) {

            Position position = new Position(getProtocolName());

            DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, String.valueOf(deviceUniqueId));
            if (deviceSession == null) {
                return null;
            }
            position.setDeviceId(deviceSession.getDeviceId());

            int hardwareType = buf.readUnsignedByte();
            switch (hardwareType & 0x1f) {
                case 12:
                    batteryConst = CELLO_TRACK_BATTERY_CONST;
                    break;
                case 5:
                    batteryConst = COMPACT_SECURITY_BATTERY_CONST;
                    break;
                default:
                    batteryConst = COMPACT_SECURITY_BATTERY_CONST;
                    break;
            }
            position.set(Position.KEY_VERSION_HW, buf.readUnsignedByte()); // Unit’s hardware version
            position.set(Position.KEY_VERSION_FW, buf.readUnsignedByte()); // Unit’s software version
            position.set("protocolVersion", buf.readUnsignedByte()); // Protocol Version Identifier

            position.set(Position.KEY_STATUS, buf.getUnsignedByte(buf.readerIndex()) & 0x0f); // Unit’s status

            int operator = (buf.readUnsignedByte() & 0xf0) << 4; // Current GSM Operator (1st nibble)
            operator += buf.readUnsignedByte(); // Current GSM Operator (2nd and 3rd nibble)

            buf.readUnsignedByte(); // Transmission Reason Specific Data
            position.set(Position.KEY_ALARM, decodeAlarm(buf.readUnsignedByte())); // Transmission reason

            position.set("mode", buf.readUnsignedByte()); // Unit’s mode of operation
            position.set(Position.PREFIX_IO + 1, buf.readUnsignedIntLE());

            operator <<= 8;
            operator += buf.readUnsignedByte();
            position.set(Position.KEY_OPERATOR, operator);

            position.set(Position.KEY_BATTERY, buf.readUnsignedByte() * batteryConst);
            // *CelloTrackBatteryConst Dodato za citanje baterije, smanjiti sledeci za jedan bajt
            buf.readUnsignedByte(); //preostala tri neiskoriscena bajta
            buf.readUnsignedByte();
            buf.readUnsignedByte();
            // position.set(Position.PREFIX_ADC + 1, buf.readUnsignedIntLE()); //
            position.set(Position.KEY_ODOMETER, buf.readUnsignedMediumLE());
            buf.skipBytes(6); // multi-purpose data

            position.set(Position.KEY_GPS, buf.readUnsignedShortLE());
            position.set("locationStatus", buf.readUnsignedByte());
            position.set("mode1", buf.readUnsignedByte());
            position.set("mode2", buf.readUnsignedByte());

            position.set(Position.KEY_SATELLITES, buf.readUnsignedByte());

            position.setValid(true);
            position.setLongitude(buf.readIntLE() / Math.PI * 180 / 100000000);
            position.setLatitude(buf.readIntLE() / Math.PI * 180 / 100000000.0);
            position.setAltitude(buf.readIntLE() * 0.01);
            position.setSpeed(UnitsConverter.knotsFromMps(buf.readIntLE() * 0.01));
            position.setCourse(buf.readUnsignedShortLE() / Math.PI * 180.0 / 1000.0);

            DateBuilder dateBuilder = new DateBuilder() //Ovde proveriti vreme
                    .setTimeReverse(buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedByte())
                    .setDateReverse(buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readUnsignedShortLE());
            position.setTime(dateBuilder.getDate());

            return position;
        }

        return null;
    }

}
