---

# 📱 Client Latency Comparison for Model-in-PNG Distribution

Below tables show approximate **end-to-end latency** (download + PNG decoding + model verification + file write)
for Android and iOS clients under Wi-Fi and LTE networks.

---

## 🧩 **1.3 MB Model (~1.0 MB over the wire after zlib compression)**

| Platform    | PNG Payload (MB) | Download @ Wi-Fi 200 Mb/s | Download @ LTE 20 Mb/s | Decompress + Verify + Write | End-to-End (Wi-Fi) |  End-to-End (LTE) |
| ----------- | ---------------: | ------------------------: | ---------------------: | --------------------------: | -----------------: | ----------------: |
| **Android** |             ~1.0 |                   ~0.04 s |                ~0.40 s |           **0.03 – 0.06 s** |  **0.16 – 0.22 s** | **0.50 – 0.60 s** |
| **iOS**     |             ~1.0 |                   ~0.04 s |                ~0.40 s |           **0.03 – 0.05 s** |  **0.15 – 0.20 s** | **0.49 – 0.58 s** |

*Includes ~0.10 s TLS/DNS overhead.*

---

## 🧠 **8 MB Model (~6.0 MB over the wire after zlib compression)**

| Platform    | PNG Payload (MB) | Download @ Wi-Fi 200 Mb/s | Download @ LTE 20 Mb/s | Decompress + Verify + Write | End-to-End (Wi-Fi) |  End-to-End (LTE) |
| ----------- | ---------------: | ------------------------: | ---------------------: | --------------------------: | -----------------: | ----------------: |
| **Android** |             ~6.0 |                   ~0.24 s |                ~2.40 s |           **0.09 – 0.16 s** |  **0.42 – 0.50 s** | **2.59 – 2.86 s** |
| **iOS**     |             ~6.0 |                   ~0.24 s |                ~2.40 s |           **0.08 – 0.14 s** |  **0.41 – 0.48 s** | **2.58 – 2.84 s** |

---

## ⚙️ **Interpretation & Notes**

* **Decompress + Verify + Write** includes:

  * PNG chunk scanning
  * zlib inflate
  * SHA-256 verification
  * Atomic write to storage
* **iOS ≈ 10–15 % faster** due to I/O and zlib optimizations.
* If using **Base64 in `iTXt`** chunks:
  ➕ ≈ 33 % payload size → add ~0.1–0.2 s CPU for small models, ~0.3–0.5 s for large.
* Compression ratio varies with model type (TFLite, ONNX, mlmodelc).
  Adjust download time linearly with actual size.
* For most real-world cases, the **network time dominates** total latency.

---

✅ *Takeaway:*
Even for 8 MB models, client-side PNG decoding adds **under 0.2 s**—network throughput is the main bottleneck.

