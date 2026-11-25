# A2D Device Setup & Deployment Checklist

- Status: Current
- Owner: project maintainer
- Last updated: 2026-11-06

Here’s a clear checklist to prepare your smartphone **before** running the script `./Deployment/a2d_deploy.sh` from the repository root.

### 1. Enable Developer Options

1. Open **Settings**
2. Go to **About phone** (or **About tablet**)
3. Find **Build number**
4. Tap **Build number** **7 times** quickly  
   → You should see the message: *“You are now a developer!”*

---

### 2. Enable USB Debugging

1. Go back to **Settings**
2. Open **System** → **Developer options**  
   (On some phones it is under **Additional settings** or **System & updates**)
3. Turn **ON** these options:

| Option                        | Required? | Notes |
|------------------------------|---------|-------|
| **USB debugging**            | Yes     | Most important |
| **Install via USB**          | Recommended | Allows installing apps over USB |
| **USB debugging (Security settings)** | Optional | Some Xiaomi / Oppo / Realme phones need this |
| **Disable permission monitoring** | Optional | Only if install fails later |

---

### 3. Connect the Phone to Your Computer

1. Unlock your phone
2. Connect it to the development computer using a **good quality USB cable** (preferably the original one)
3. On the phone you should see a popup:

   > **Allow USB debugging?**  
   > The computer's RSA key fingerprint is: ...

4. Check the box **“Always allow from this computer”**
5. Tap **Allow** / **OK**

---

### 4. Quick Verification (Optional but Recommended)

After connecting, you can quickly test before running the full script:

```bash
adb devices
```

**Good result:**
```
List of devices attached
XXXXXXXXXXXX    device
```

**Bad results & fixes:**

| Output              | Meaning                        | Fix |
|---------------------|--------------------------------|-----|
| `unauthorized`      | You didn’t allow the RSA prompt | Unlock phone and tap Allow |
| empty list          | Cable / driver issue           | Try another cable or USB port |
| `offline`           | Connection problem             | Unplug → replug + revoke USB debugging authorizations |

---

### Extra Tips for Common Brands

- **Xiaomi / Redmi / Poco**: Also enable **USB debugging (Security settings)** and **Install via USB**
- **Samsung**: Usually works with just USB debugging
- **Oppo / Realme / Vivo**: May need **Disable permission monitoring**
- **Pixel / OnePlus / Nothing**: Usually the simplest

---

### 5. Run deploy script

Once you have done the steps above and `adb devices` shows your phone as `device`, you are ready to run the script from the repository root:

```bash
./Deployment/a2d_deploy.sh
```

The script resolves the APK relative to its own location (`A2D/app/build/outputs/apk/debug/app-debug.apk`), installs it with `adb install -r`, then launches the app using the manifest's real launcher component:

```bash
com.a2d.app/.ui.MainActivity
```

If the APK has not been built yet, build it first and return to the repository root:

```bash
cd A2D && ./gradlew assembleDebug && cd ..
```

If the script doesn’t have execute permission yet, fix it with this:

```bash
chmod +x Deployment/a2d_deploy.sh
```

Then run it again:

```bash
./Deployment/a2d_deploy.sh
```

---

### Alternative (if you prefer not to change permissions)

You can also run it directly with bash:

```bash
bash Deployment/a2d_deploy.sh
```

Both methods work. The `chmod +x` way is cleaner for repeated use.