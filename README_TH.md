# ReflexCare USB Bridge (ต้นแบบ 0.1.0)

แอป Android สำหรับให้หน้าเว็บใน WebView รับส่ง Serial ผ่าน USB Host กับบอร์ด ESP32 ที่มี CH340

## สิ่งที่มีในต้นแบบ
- ค้นหา USB Serial ด้วย `UsbSerialProber`
- ขอสิทธิ์ USB จาก Android
- เปิดพอร์ตที่ 115200, 8-N-1
- ส่งข้อมูลจาก JavaScript ผ่าน `AndroidSerial.send("ข้อความ\\n")`
- รับข้อมูลผ่าน event `usbserial-data` โดย `detail.base64` เป็น Base64 ของ bytes ที่ได้รับ
- โหลด URL หน้าเว็บ ReflexCare ผ่านช่อง URL

ไลบรารีอ้างอิง `usb-serial-for-android` รุ่น 3.12.0 (มีการเพิ่มการรองรับ CH34x RTS/CTS)  
https://github.com/mik3y/usb-serial-for-android/releases

## วิธีเปิดโปรเจกต์
1. แตก ZIP
2. เปิดโฟลเดอร์ `ReflexCareUSBBridge` ด้วย Android Studio
3. ให้ Gradle Sync ดาวน์โหลด dependencies (ต้องมีอินเทอร์เน็ตในคอมพิวเตอร์)
4. Build > Build APK(s)
5. ติดตั้ง APK บนจอ Android โดยอนุญาต USB permission เมื่อเสียบบอร์ด

## API ที่หน้าเว็บเรียกได้
```javascript
// ส่งข้อความ (ต้องเชื่อมต่อก่อน)
AndroidSerial.send("LED_ON\\n");

// ตรวจสถานะ
const connected = AndroidSerial.isConnected();

// รับข้อมูล: event detail.base64 คือ Base64
window.addEventListener("usbserial-data", (event) => {
  const raw = atob(event.detail.base64);
  const bytes = Uint8Array.from(raw, c => c.charCodeAt(0));
  const text = new TextDecoder().decode(bytes);
  console.log("ESP32:", text);
});
```

## ข้อจำกัดที่ต้องทราบ
- นี่คือซอร์สโปรเจกต์ ไม่ใช่ APK ที่คอมไพล์แล้ว
- ยังไม่ได้ทดสอบบนจอ ACONATIC 32SMU9000AN และบอร์ดจริง
- ต้องใช้สาย USB-C ที่ส่งข้อมูลได้ ไม่ใช่สายชาร์จอย่างเดียว
- Android ต้องรองรับ USB Host/OTG และจ่ายไฟให้บอร์ดได้เพียงพอ
- บางบอร์ด ESP32-WROOM ใช้ CH340 แต่ต้องยืนยัน VID/PID และรูปแบบบอร์ดจากเครื่องจริง
- JavaScript Bridge ควรเปิดให้เฉพาะหน้าเว็บที่เชื่อถือได้เท่านั้น อย่าโหลด URL ที่ไม่รู้จัก เพราะหน้าเว็บนั้นสามารถเรียก API ส่งข้อมูล Serial ได้
- สำหรับเว็บที่โฮสต์ HTTPS ให้ใส่ URL จริงในช่องด้านบน


## URL ที่ตั้งค่าเริ่มต้น
แอปจะเติม URL `https://torcm.free.nf/reflextab/5.html` ในช่อง URL ให้อัตโนมัติเมื่อเปิดแอป ผู้ใช้ยังแก้ไข URL ได้ก่อนกดเปิดหน้าเว็บ
