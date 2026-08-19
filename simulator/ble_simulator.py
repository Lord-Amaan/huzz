import asyncio
import struct
import time
import uuid
from bleak import BleakScanner, BleakClient

# Constants
SERVICE_UUID = "E47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C".lower()
CHARACTERISTIC_UUID = "B1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D".lower()
PC_PEER_ID = bytes.fromhex("1122334455667788")

# Packet Types
MESSAGE = 1
ANNOUNCEMENT = 2

def build_packet(type_byte, payload_bytes):
    version = 1
    ttl = 7
    timestamp = int(time.time() * 1000)
    flags = 0
    payload_len = len(payload_bytes)
    
    # Format: [version:1][type:1][ttl:1][timestamp:8][flags:1][payloadLen:2][senderID:8][payload:N]
    header = struct.pack(">BBBQBH", version, type_byte, ttl, timestamp, flags, payload_len)
    return header + PC_PEER_ID + payload_bytes

def parse_packet(data):
    if len(data) < 14:
        return None
    version, type_byte, ttl, timestamp, flags, payload_len = struct.unpack(">BBBQBH", data[:14])
    sender_id = data[14:22].hex()
    
    # Check flags for recipient
    has_recipient = (flags & 0x01) != 0
    payload_start = 22
    recipient_id = None
    if has_recipient:
        recipient_id = data[22:30].hex()
        payload_start = 30
        
    payload = data[payload_start:payload_start+payload_len]
    return {
        "type": type_byte,
        "sender": sender_id,
        "recipient": recipient_id,
        "payload": payload
    }

async def notification_handler(sender, data):
    packet = parse_packet(data)
    if not packet:
        print(f"<- Received unknown data (len: {len(data)})")
        return
        
    p_type = packet["type"]
    sender_id = packet["sender"]
    payload = packet["payload"]
    
    if p_type == MESSAGE:
        try:
            # Payload format: nickname\x00message
            parts = payload.split(b"\x00", 1)
            nick = parts[0].decode('utf-8')
            msg = parts[1].decode('utf-8') if len(parts) > 1 else ""
            print(f"\n💬 [MESSAGE] {nick}: {msg}")
        except:
            print(f"\n💬 [MESSAGE RAW] {sender_id}: {payload}")
            
    elif p_type == ANNOUNCEMENT:
        try:
            nick = payload.decode('utf-8')
            print(f"\n📡 [ANNOUNCEMENT] {nick} joined the mesh!")
        except:
            print(f"\n📡 [ANNOUNCEMENT RAW] {sender_id}")
    else:
        print(f"\n📦 [TYPE {p_type}] from {sender_id}")

async def run_simulator():
    print("🔍 Scanning for Huzz mesh devices...")
    
    target_device = None
    while target_device is None:
        devices = await BleakScanner.discover(timeout=5.0, return_adv=True)
        for address, (d, adv_data) in devices.items():
            if SERVICE_UUID in adv_data.service_uuids:
                target_device = d
                break
        if not target_device:
            print("⏳ Still scanning... make sure Huzz app is open, you clicked 'Join Mesh Network', and Bluetooth is on.")
            
    print(f"✅ Found Huzz node! Address: {target_device.address}")
    
    print(f"🔗 Connecting to {target_device.address}...")
    async with BleakClient(target_device, timeout=20.0) as client:
        print(f"✅ Connected! Subscribing to messages...")
        await client.start_notify(CHARACTERISTIC_UUID, notification_handler)
        
        # Send our announcement
        nick_bytes = "PC Simulator".encode('utf-8')
        ann_packet = build_packet(ANNOUNCEMENT, nick_bytes)
        print("-> Sending Announcement...")
        await client.write_gatt_char(CHARACTERISTIC_UUID, ann_packet, response=False)
        
        await asyncio.sleep(1.0)
        
        # Send a chat message
        msg_payload = "PC Simulator\x00Hello from Windows!".encode('utf-8')
        msg_packet = build_packet(MESSAGE, msg_payload)
        print("-> Sending 'Hello from Windows!'...")
        await client.write_gatt_char(CHARACTERISTIC_UUID, msg_packet, response=False)
        
        print("\n👂 Listening for messages... (Type Ctrl+C to exit)")
        try:
            while True:
                await asyncio.sleep(1)
        except asyncio.CancelledError:
            pass

if __name__ == "__main__":
    try:
        asyncio.run(run_simulator())
    except KeyboardInterrupt:
        print("\nExiting...")
