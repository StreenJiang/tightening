### MID 0061 Revision 1 (Data Field)

| Parameter | Byte | Value |
| :--- | :--- | :--- |
| **Cell ID** | 21-22 | 01 |
| | 23-26 | The cell ID is four bytes long and specified by four ASCII digits. Range: 0000-9999. |
| **Channel ID** | 27-28 | 02 |
| | 29-30 | The channel ID is two bytes long and specified by two ASCII digits. Range: 00-99. |
| **Torque controller Name** | 31-32 | 03 |
| | 33-57 | The controller name is 25 bytes long and is specified by 25 ASCII characters. |
| **VIN Number** | 58-59 | 04 |
| | 60-84 | The VIN number is 25 bytes long and is specified by 25 ASCII characters. |
| **Job ID** | 85-86 | 05 |
| | 87-88 | The Job ID is two bytes long and specified by two ASCII digits. Range: 00-99. |
| **Parameter set ID** | 89-90 | 06 |
| | 91-93 | The parameter set ID is three bytes long and specified by three ASCII digits. Range: 000-999. |
| **Batch size** | 94-95 | 07 |
| | 96-99 | This parameter gives the total number of tightening in the batch. The batch size is four bytes long and specified by four ASCII digits. Range: 0000-9999. |
| **Batch counter** | 100-101 | 08 |
| | 102-105 | The batch counter information is four bytes long specifying and specified by four ASCII digits. Range: 0000-9999. |
| **Tightening Status** | 106-107 | 09 |
| | 108 | The tightening status is one byte long and specified by one ASCII digit. 0=tightening NOK, 1=tightening OK. |
| **Torque status** | 109-110 | 10 |
| | 111 | 0=Low, 1=OK, 2=High |
| **Angle status** | 112-113 | 11 |
| | 114 | 0=Low, 1=OK, 2=High |
| **Torque Min limit** | 115-116 | 12 |
| | 117-122 | The torque min limit is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and is specified by six ASCII digits. |
| **Torque Max limit** | 123-124 | 13 |
| | 125-130 | The torque max limit is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and is specified by six ASCII digits. |
| **Torque final target** | 131-132 | 14 |
| | 133-138 | The torque final target is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and is specified by six ASCII digits. |
| **Torque** | 139-140 | 15 |
| | 141-146 | The torque value is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and is specified by six ASCII digits. |
| **Angle Min** | 147-148 | 16 |
| | 149-153 | The angle min value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Angle Max** | 154-155 | 17 |
| | 156-160 | The angle max value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Final Angle Target** | 161-162 | 18 |
| | 163-167 | The target angle value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Angle** | 168-169 | 19 |
| | 170-174 | The turning angle value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Time stamp** | 175-176 | 20 |
| | 177-195 | Time stamp for each tightening. It is 19 bytes long and is specified by 19 ASCII characters (YYYY-MM-DD HH:MM:SS). |
| **Date/time of last change in parameter set settings** | 196-197 | 21 |
| | 198-216 | Time stamp for the last change in the current parameter set settings. It is 19 bytes long and is specified by 19 ASCII characters (YYYY-MM-DD HH:MM:SS). |
| **Batch status** | 217-218 | 22 |
| | 219 | The batch status is specified by one ASCII character. 0=batch NOK (batch not completed), 1=batch OK, 2=batch not used. |
| **Tightening ID** | 220-221 | 23 |
| | 222-231 | The tightening ID is a unique ID for each tightening result. It is incremented after each tightening. 10 ASCII digits. Max 4294967295. |


### MID 0061 Revision 2

| Parameter | Byte | Value |
| :--- | :--- | :--- |
| **Cell ID** | 21-22 | 01 |
| | 23-26 | The cell ID is four bytes long and specified by four ASCII digits. Range: 0000-9999. |
| **Channel ID** | 27-28 | 02 |
| | 29-30 | The channel ID is two bytes long and specified by two ASCII digits. Range: 00-99. |
| **Torque controller Name** | 31-32 | 03 |
| | 33-57 | The controller name is 25 bytes long and is specified by 25 ASCII characters. |
| **VIN Number** | 58-59 | 04 |
| | 60-84 | The VIN number is 25 bytes long and is specified by 25 ASCII characters. |
| **Job ID** | 85-86 | 05 |
| | 87-88 | The Job ID is two bytes long and specified by two ASCII digits. Range: 00-99. |
| **Parameter set number** | 89-90 | 06 |
| | 91-93 | The parameter set ID is three bytes long and specified by three ASCII digits. Range: 000-999. |
| **Strategy** | 94-95 | 07 |
| | 96-98 | The strategies currently run by the controller. It is two bytes long and specified by two ASCII digits. Range: 00-99.<br>The corresponding strategies are:<br>01=Torque control, 02=Torque control / angle monitoring, 03=Torque control / angle control AND 04=Angle control / torque monitoring, 05=DS control, 06=DS control / torque monitoring, 07=Reverse angle, 08=Reverse torque, 09=Click wrench, 10=Rotate spindle forward, 11=Torque control angle control OR, 12=Rotate spindle reverse, 13=Home position forward, 14=EP Monitoring, 15=Yield, 16=EP Fixed, 17=EP Control, 18=EP Angle shutoff, 19=Yield / torque control OR, 20=Snug gradient, 21=Residual torque / Time 22=Residual torque / Angle, 23=Breakaway angle, 24=Loose and tightening, 25=Home position reverse, 26=PVT comp with Snug 99=No strategy |
| **Strategy options** | 100-101 | 08 |
| | 102-106 | Five bytes long bit field.<br>Bit 0: Angle<br>Bit 1: Torque<br>Bit 2: Batch<br>Bit 3: PVT Monitoring<br>Bit 4: PVT Compensate<br>Bit 5: Self-tap<br>Bit 6: Rundown<br>Bit 7: CM<br>Bit 8: DS control<br>Bit 9: Click Wrench<br>Bit 10: RBW Monitoring |
| **Batch size** | 107-108 | 09 |
| | 109-112 | This parameter gives the total number of tightening in the batch. The batch size is four bytes long and specified by four ASCII digits. Range: 0000-9999. |
| **Batch counter** | 113-114 | 10 |
| | 115-118 | The batch counter information is four bytes long specifying and specified by four ASCII digits. Range: 0000-9999. |
| **Tightening Status** | 119-120 | 11 |
| | 121 | The tightening status is one byte long and is specified by one ASCII digit. 0=tightening NOK, 1=tightening OK.<br>**Note:** For Ford the status is built on certain "Tightening error status" bits and "Result type", see fields below. See Ford appendix for detailed description. |
| **Batch status** | 122-123 | 12 |
| | 124 | The batch status is specified by one ASCII character. 0=batch NOK (batch not completed), 1=batch OK, 2=batch not used. |
| **Torque status** | 125-126 | 13 |
| | 127 | 0=Low, 1=OK, 2=High |
| **Angle status** | 128-129 | 14 |
| | 130 | 0=Low, 1=OK, 2=High |
| **Rundown angle status** | 131-132 | 15 |
| | 133 | 0=Low, 1=OK, 2=High |
| **Current Monitoring Status** | 134-135 | 16 |
| | 136 | 0=Low, 1=OK, 2=High |
| **Self-tap status** | 137-138 | 17 |
| | 139 | 0=Low, 1=OK, 2=High |
| **Prevail Torque monitoring status** | 140-141 | 18 |
| | 142 | 0=Low, 1=OK, 2=High |
| **Prevail Torque compensate status** | 143-144 | 19 |
| | 145 | 0=Low, 1=OK, 2=High |
| **Tightening error status** | 146-147 | 20 |
| | 148-157 | Ten bytes long bit field. Tightening error bits show what went wrong with the tightening.<br>Bit 1: Rundown angle max shut off<br>Bit 2: Rundown angle min shut off<br>Bit 3: Torque max shut off<br>Bit 4: Angle max shut off<br>Bit 5: Self-tap torque max shut off<br>Bit 6: Self-tap torque min shut off<br>Bit 7: Prevail torque max shut off<br>Bit 8: Prevail torque min shut off<br>Bit 9: Prevail torque compensate overflow<br>Bit 10: Current monitoring max shut off<br>Bit 11: Post view torque min torque shut off<br>Bit 12: Post view torque max torque shut off<br>Bit 13: Post view torque Angle too small<br>Bit 14: Trigger lost<br>Bit 15: Torque less than target<br>Bit 16: Tool hot<br>Bit 17: Multistage abort<br>Bit 18: Resh<br>Bit 19: DS measure failed<br>Bit 20: Current limit reached<br>Bit 21: End Time out shutoff<br>Bit 22: Remove fastener limit exceeded<br>Bit 23: Disable drive<br>Bit 24: Transducer lost<br>Bit 25: Transducer shorted<br>Bit 26: Transducer corrupt<br>Bit 27: Sync timeout<br>Bit 28: Dynamic current monitoring min<br>Bit 29: Dynamic current monitoring max<br>Bit 30: Angle max monitor<br>Bit 31: Yield nut off<br>Bit 32: Yield too few samples |
| **Torque Min limit** | 158-159 | 21 |
| | 160-165 | The torque min limit is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and is specified by six ASCII digits. |
| **Torque Max limit** | 166-167 | 22 |
| | 168-173 | The torque max limit is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and is specified by six ASCII digits. |
| **Torque final target** | 174-175 | 23 |
| | 176-181 | The torque final target is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and is specified by six ASCII digits. |
| **Torque** | 182-183 | 24 |
| | 184-189 | The torque value is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and is specified by six ASCII digits. |
| **Angle Min** | 190-191 | 25 |
| | 192-196 | The angle min value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Angle Max** | 197-198 | 26 |
| | 199-203 | The angle max value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Final Angle Target** | 204-205 | 27 |
| | 206-210 | The target angle value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Angle** | 211-212 | 28 |
| | 213-217 | The turning angle value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Rundown angle Min** | 218-219 | 29 |
| | 220-224 | The tightening angle min value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Rundown angle Max** | 225-226 | 30 |
| | 227-231 | The tightening angle max value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Rundown angle** | 232-233 | 31 |
| | 234-238 | The tightening angle value reached in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Current Monitoring Min** | 239-240 | 32 |
| | 241-243 | The current monitoring min limit in percent is three bytes long and is specified by three ASCII digits. Range: 000-999. |
| **Current Monitoring Max** | 244-245 | 33 |
| | 246-248 | The current monitoring max limit in percent is three bytes long and is specified by three ASCII digits. Range: 000-999. |
| **Current Monitoring Value** | 249-250 | 34 |
| | 251-253 | The current monitoring value in percent is three bytes long and is specified by three ASCII digits. Range: 000-999. |
| **Self-tap min** | 254-255 | 35 |
| | 256-261 | The self-tap min limit is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and specified by six ASCII digits. |
| **Self-tap max** | 262-263 | 36 |
| | 264-269 | The self-tap max limit is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and specified by six ASCII digits. |
| **Self-tap torque** | 270-271 | 37 |
| | 272-277 | The self-tap torque is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and specified by six ASCII digits. |
| **Prevail torque monitoring min** | 278-279 | 38 |
| | 280-285 | The PVTmin limit is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and specified by six ASCII digits. |
| **Prevail torque monitoring max** | 286-287 | 39 |
| | 288-293 | The PVT max limit is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and specified by six ASCII digits. |
| **Prevail torque** | 294-295 | 40 |
| | 296-301 | The prevail torque value is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and is specified by six ASCII digits. |
| **Tightening ID** | 302-303 | 41 |
| | 304-313 | The tightening ID is a unique ID. It is incremented after each tightening. It is ten bytes long and specified by ten ASCII digits. Max 4294967295. |
| **Job sequence number** | 314-315 | 42 |
| | 316-320 | The Job sequence number is unique for each Job. All tightenings performed in the same Job are stamped with the same Job sequence number. It is specified by five ASCII digits. Range: 00000-65535. |
| **Sync tightening ID** | 321-322 | 43 |
| | 323-327 | The sync tightening ID is a unique ID for each sync tightening result. Each individual result of each spindle is stamped with this ID. The tightening ID is incremented after each sync tightening. It is specified by five ASCII digits. Range: 00000-65535. |
| **Tool serial number** | 328-329 | 44 |
| | 330-343 | The Tool serial number is specified by 14 ASCII characters. |
| **Time stamp** | 344-345 | 45 |
| | 346-364 | Time stamp for the tightening. It is 19 bytes long and is specified by 19 ASCII characters (YYYY-MM-DD HH:MM:SS). |
| **Date/time of last change in parameter set settings** | 365-366 | 46 |
| | 367-385 | Time stamp for the last change in the current parameter set settings. It is 19 bytes long and is specified by 19 ASCII characters (YYYY-MM-DD HH:MM:SS). |


### MID 0061 Revision 3

| Parameter | Byte | Value |
| :--- | :--- | :--- |
| **Parameter set Name** | 386-387 | 47 |
| | 388-412 | The parameter set name is 25 bytes long and is specified by 25 ASCII characters. |
| **Torque values Unit** | 413-414 | 48 |
| | 415 | The unit in which the torque values are sent. The torque values unit is one byte long and is specified by one ASCII digit.<br>1=Nm, 2=Lbf.ft, 3=Lbf.In, 4=Kpm<br>5=Kgf.cm, 6=ozf.in, 7=%, 8= Ncm |
| **Result type** | 416-417 | 49 |
| | 418-419 | The result type is two bytes long and specified by two ASCII digits.<br>1=Tightening, 2=Loosening, 3=Batch Increment<br>4=Batch decrement, 5=Bypass parameter set result<br>6=Abort Job result, 7=Sync tightening,<br>8=Reference setup |

---

### MID 0061 Revision 4

| Parameter | Byte | Value |
| :--- | :--- | :--- |
| **Identifier result part 2** | 420-421 | 50 |
| | 422-446 | The identifier result part 2 is 25 bytes long and is specified by 25 ASCII characters. |
| **Identifier result part 3** | 447-448 | 51 |
| | 449-473 | The identifier result part 3 is 25 bytes long and is specified by 25 ASCII characters. |
| **Identifier result part 4** | 474-475 | 52 |
| | 476-500 | The identifier result part 4 is 25 bytes long and is specified by 25 ASCII characters. |

> **Note:** The identifier result parts will only be set if the multiple identifier option has been activated in the controller.

---

### MID 0061 Revision 5

| Parameter | Byte | Value |
| :--- | :--- | :--- |
| **Customer tightening error code** | 501-502 | 53 |
| | 503-506 | The customer tightening error code is 4 byte long and is specified by 4 ASCII characters. |

---

### Table 67 MID 0061 Revision 6

| Parameter | Byte | Value |
| :--- | :--- | :--- |
| **Prevail Torque compensate value** | 507-508 | 54 |
| | 509-514 | The PVT compensate torque value. It is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and specified by six ASCII digits. |
| **Tightening error status 2** | 515-516 | 55 |
| | 517-526 | Bit field, Tightening error bits 2 shows what went wrong with the tightening.<br>Bit 1: Drive deactivated<br>Bit 2: Tool stall<br>Bit 3: Drive hot<br>Bit 4: Gradient monitoring high<br>Bit 5: Gradient monitoring low<br>Bit 6: Reaction bar failed<br>Bit 7: Snug Max<br>Bit 8: Cycle abort<br>Bit 9: Necking failure<br>Bit 10: Effective loosening<br>Bit 11: Over speed<br>Bit 12: No residual Torque<br>Bit 13: Positioning fail<br>Bit 14: Snug Mon. Low<br>Bit 15: Snug Mon. High<br>Bit 16: Dynamic Min. Current<br>Bit 17: Dynamic Mac Current<br>Bit 18: Latent result<br>Bit 19-32: Reserved |

---

### MID 0061 Revision 7

| Parameter | Byte | Value |
| :--- | :--- | :--- |
| **Compensated angle** | 527-528 | 56 |
| | 529-535 | The compensated angle value is multiplied by 100 and sent as an integer. It is seven bytes long and specified by seven ASCII digits. |
| **Final Angle Decimal** | 536-537 | 57 |
| | 538-544 | The turning angle value is multiplied by 100 and sent as an integer (2 decimals truncated). It is seven bytes long and is specified by seven ASCII digits. |

---

### MID 0061 Revision 998

| Parameter | Byte | Value |
| :--- | :--- | :--- |
| **Number of stages in multistage** | 527-528 | 56 |
| | 529-530 | The total number of stages to be run for this tightening. It is two bytes long and specified by two ASCII digits. |
| **Number of stage results** | 531-532 | 57 |
| | 533-534 | Number of run stages. It is two bytes long and specified by two ASCII digits. For each completed stage the final torque and the final angle are reported. |
| **Stage result** | 535-536 | 58 |
| | 537- +11 x number of stage results | **Byte 1-6:** The stage torque value. The torque is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and specified by six ASCII digits.<br>**Byte 7-11:** The turning angle stage value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |


### MID 0061 Light, revision 999

| Parameter | Byte | Value |
| :--- | :--- | :--- |
| **VIN Number** | 21-45 | The VIN number is 25 bytes long and is specified by 25 ASCII characters taken. |
| **Job ID** | 46-47 | This is the Job ID. It is two bytes long and specified by two ASCII digits. Range: 00-99. |
| **Parameter set ID** | 48-50 | The parameter set ID is three bytes long and specified by three ASCII digits. Range: 000-999. |
| **Batch size** | 51-54 | This parameter gives the total number of tightening in the batch. It is four bytes long and specified by four ASCII digits. Range: 0000-9999. |
| **Batch counter** | 55-58 | The batch counter is four bytes long and specified by four ASCII digits. Range: 0000-9999. |
| **Batch status** | 59 | The batch status is specified by one ASCII character. 0=batch NOK (batch not completed), 1=batch OK, 2=batch not used. |
| **Tightening status** | 60 | The tightening status is one byte long and specified by one ASCII digit. 0=tightening NOK, 1=tightening OK. |
| **Torque status** | 61 | 0=Low, 1=OK, 2=High |
| **Angle status** | 62 | 0=Low, 1=OK, 2=High |
| **Torque** | 63-68 | The torque value is multiplied by 100 and sent as an integer (2 decimals truncated). It is six bytes long and is specified by six ASCII digits. |
| **Angle** | 69-73 | The turning angle value in degrees. Each turn represents 360 degrees. It is five bytes long and specified by five ASCII digits. Range: 00000-99999. |
| **Time stamp** | 74-92 | Time stamp for the tightening. It is 19 bytes long and is specified by 19 ASCII characters (YYYY-MM-DD:HH:MM:SS). |
| **Date/time of last change in parameter set settings** | 93-111 | Time stamp for the last change in the current parameter set settings. It is 19 bytes long and is specified by 19 ASCII characters (YYYY-MM-DD:HH:MM:SS). |
| **Tightening ID** | 112-121 | The tightening ID is a unique ID for each tightening result. It is incremented after each tightening. 10 ASCII digits. Max 4294967295. |

> **Note:** The MID 0061 light revision 999 is intended to be used by integrators with limited receiving capability (small receive buffer). In order to limit the size of the MID 0061 as much as possible the parameter IDs usually sent in the message has been removed.
