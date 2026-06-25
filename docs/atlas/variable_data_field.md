### Variable Data Field Structure (General)

| Parameter | Size (bytes) | Data type | Description |
| :--- | :--- | :--- | :--- |
| **Number of data fields** | 3 | UI | The number of variable data fields in this section. Must be the first field of each variable data block. Sent as "000" if no fields exist. |
| **Data fields** (repeated) | Vary | – | This section is repeated *Number of data fields* times. Each occurrence consists of the following sub‑fields: |
| └ **Parameter ID (PID)** | 5 | UI | Identifies the parameter (see chapter 6). Available PIDs depend on system type. |
| └ **Length** | 3 | UI | Length of the following data value in bytes. |
| └ **Data Type** | 2 | UI | Data type of the value (see data type definitions). |
| └ **Unit** | 3 | UI | Unit of the value (see unit table). |
| └ **Step no.** | 4 | UI | Step number for the result variable. Sent as "0000" if not relevant. |
| └ **Data value** | *Length* (variable) | Depends on Data Type | The actual data value. Its size and format are determined by the preceding *Length* and *Data Type* fields. |

---

**Note:**
- All string fields are left‑adjusted and padded with spaces.
- All numeric fields are right‑adjusted and padded with zeros (`0`).