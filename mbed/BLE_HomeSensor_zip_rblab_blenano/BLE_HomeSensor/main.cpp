/* mbed Microcontroller Library
 * Copyright (c) 2006-2013 ARM Limited
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

#include "mbed.h"
#include "BLEDevice.h"

#include "UARTService.h"

#define NEED_CONSOLE_OUTPUT 0 /* Set this if you need debug messages on the console;
                               * it will have an impact on code-size and power consumption. */

#if NEED_CONSOLE_OUTPUT
#define DEBUG(...) { printf(__VA_ARGS__); }
#else
#define DEBUG(...) /* nothing */
#endif /* #if NEED_CONSOLE_OUTPUT */

#define MSG_LEN_MAX 32

#define LED_ON  0
#define LED_OFF 1

BLEDevice  ble;
DigitalOut led1(LED1);
AnalogIn ain(p5);

UARTService *uartServicePtr;

uint8_t         TestPayload[6] = { 'H', 'E', 'L', 'L', 'O', '!' };
char            msg[MSG_LEN_MAX] ="" ;
 
void disconnectionCallback(Gap::Handle_t handle, Gap::DisconnectionReason_t reason)
{
    DEBUG("Disconnected!\n\r");
    DEBUG("Restarting the advertising process\n\r");
    ble.startAdvertising();
}

void onDataWritten(const GattCharacteristicWriteCBParams *params)
{
    if ((uartServicePtr != NULL) && (params->charHandle == uartServicePtr->getTXCharacteristicHandle())) {
        uint16_t bytesRead = params->len;
        DEBUG("received %u bytes\n\r", bytesRead);
        ble.updateCharacteristicValue(uartServicePtr->getRXCharacteristicHandle(), params->data, bytesRead);
    }
}

void periodicCallback(void)
{
    float v;
    uint8_t strlength;

    v = ain.read() * 3.3;
    sprintf(msg,"v=%4.2f\0", v);
    for(strlength=0; strlength<MSG_LEN_MAX; ++strlength) {
        if(msg[strlength]=='\0')break;
    }
    ble.updateCharacteristicValue(uartServicePtr->getRXCharacteristicHandle(), (uint8_t *)msg, strlength);
}

int main(void)
{
    led1 = LED_OFF;
    Ticker ticker;

    DEBUG("Initialising the nRF51822\n\r");
    ble.init();
    ble.onDisconnection(disconnectionCallback);
    ble.onDataWritten(onDataWritten);

    /* setup advertising */
    ble.accumulateAdvertisingPayload(GapAdvertisingData::BREDR_NOT_SUPPORTED);
    ble.setAdvertisingType(GapAdvertisingParams::ADV_CONNECTABLE_UNDIRECTED);
    ble.accumulateAdvertisingPayload(GapAdvertisingData::SHORTENED_LOCAL_NAME,
                                     (const uint8_t *)"BLE HomeSensor", sizeof("BLE HomeSensor") - 1);
    ble.accumulateAdvertisingPayload(GapAdvertisingData::COMPLETE_LIST_128BIT_SERVICE_IDS,
                                     (const uint8_t *)UARTServiceUUID_reversed, sizeof(UARTServiceUUID_reversed));

    ble.setAdvertisingInterval(Gap::MSEC_TO_ADVERTISEMENT_DURATION_UNITS(1000));
    ble.startAdvertising();

    UARTService uartService(ble);
    uartServicePtr = &uartService;

    ticker.attach(periodicCallback, 1);
    while (true) {
        ble.waitForEvent();
    }
}
