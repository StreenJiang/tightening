### MID 0900 Revision 1 (Data Field)

This message contains all trace curve data. The Data Field begins at byte 21 after the message header.

| Parameter | Byte | Value |
| :--- | :--- | :--- |
| **Result Data Identifier** | 21-30 | 01 |
| | 21-30 | The Result Data Identifier is a unique ID for each operation result within the system. 10 bytes, specified by 10 ASCII digits. |
| **Time Stamp** | 31-49 | 02 |
| | 31-49 | Time stamp for each operation. 19 bytes, specified by 19 ASCII characters in the format `YYYY-MM-DD:HH:MM:SS`. |
| **Number of PID's** | 50-52 | 03 |
| | 50-52 | The number of variable data fields in the telegram. 3 bytes, specified by 3 ASCII digits. If no data fields exist, "000" is sent. |
| **Data Fields** | 53+ | 04 |
| | Varies | This section is repeated "Number of PID's" times. Each field has the structure below. If Number of PID's = 000, this section is not sent. |
| └ **Parameter ID (PID)** | Varies | 5 bytes, UI. Available PID's vary depending on system type. |
| └ **Length** | Varies | 3 bytes, UI. Length of data value. |
| └ **Data Type** | Varies | 2 bytes, UI. Data type of the data value. |
| └ **Unit** | Varies | 3 bytes, UI. Unit of the data. |
| └ **Step no.** | Varies | 4 bytes, UI. The step number for the trace result variable. Sent as 0000 if not relevant (stage Index). |
| └ **Data value** | Varies | Varies. The data value. Length and type depend on the Data Type field. |
| **Trace Type** | Varies | 05 |
| | Varies | 2 bytes, UI. Type of the trace curve. 1 = Angle trace, 2 = Torque trace, 3 = Current trace, 4 = Gradient trace, 5 = Stroke trace, 6 = Force trace. |
| **Transducer Type** | Varies | 06 |
| | Varies | 2 bytes, UI. Identifies the transducer used. 1 = transducer 1, 2 = transducer 2, etc. |
| **Unit** | Varies | 07 |
| | Varies | 3 bytes, UI. Unit of trace curve, according to the Unit types table (e.g., 001 = Nm). |
| **Number of parameter data fields** | Varies | 08 |
| | Varies | 3 bytes, UI. The number of variable data fields in the telegram. If no data fields exist, "000" is sent. |
| **Data Fields** | Varies | 09 |
| | Varies | This section is repeated "Number of parameter data fields" times. Structure is the same as the Data Fields above (PID, Length, Data Type, Unit, Step no., Data value). |
| **Number of resolution fields** | Varies | 10 |
| | Varies | 3 bytes, UI. The number of different resolution fields in this telegram. If no data fields exist, "000" is sent. |
| **Resolution Fields** | Varies | 11 |
| | Varies | This section is repeated "Number of resolution fields" times. Defines the time interval between two consecutive samples in the trace curve. |
| └ **First index** | Varies | 5 bytes, UI. The first index in the trace data where this resolution is valid. |
| └ **Last Index** | Varies | 5 bytes, UI. The last index in the trace data where this resolution is valid. |
| └ **Length** | Varies | 3 bytes, UI. Length of the time value. |
| └ **Data Type** | Varies | 2 bytes, UI. Data type of the time value. |
| └ **Unit** | Varies | 3 bytes, UI. Unit of the time value. |
| └ **Time value** | Varies | Varies. The time between two consecutive samples. Length and type depend on the Data Type field. |
| **Number of trace samples** | Varies | 12 |
| | Varies | 5 bytes, UI. Number of samples in the trace. |
| **NUL character** | Varies | 13 |
| | 1 byte | A NUL character (0x00) is sent here to separate text and binary data. |
| **Trace sample** | Varies | 14 |
| | Varies | Repeated "Number of trace samples" times. Each point in the trace is sent as a 2-byte binary value. To calculate physical values, divide by coefficient (PID 02213) or multiply (PID 02214). |
