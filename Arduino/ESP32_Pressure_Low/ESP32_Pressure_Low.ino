#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Wire.h>
#include <esp_sleep.h>

// RTC-переменная
RTC_DATA_ATTR bool sleepFlag = false;

// BLE параметры
BLEServer* pServer = nullptr;
BLECharacteristic* pTempCharacteristic = nullptr;
BLECharacteristic* batteryCharacteristic = nullptr;
bool deviceConnected = false;
bool oldDeviceConnected = false;

// UUID
#define SERVICE_UUID        "4fafc201-1fb5-459e-8fcc-c5c9c331914b"
#define TEMP_CHAR_UUID      "beb5483e-36e1-4688-b7f5-ea07361b26a8"
#define BATTERY_CHAR_UUID   "e1f00324-70ea-4fff-b0b1-94f33872dff9"

// Константа таймаута
const uint64_t DEEP_SLEEP_TIMEOUT = 7200000 * 1000; // 2 часа в микросекундах

// Параметры светодиода
const int LED_PIN = 5;         // GPIO5 для светодиода
unsigned long previousMillis = 0;
int ledState = LOW;

// Управление преобразователем
const int MT3608_EN = 6;       // GPIO6 для управления MT3608

// Параметры ADS1115
const int ADS_ADDR = 0x48;     // Адрес I2C (1001000)
const int ADS_CONFIG_CH1 = 0xC3E3; // Конфигурационный регистр для канала 1 (пин 4)
const int ADS_CONFIG_CH2 = 0xD3E3; // Конфигурационный регистр для канала 2 (пин 5)
int16_t base_value = 0;        // Базовое значение для калибровки
uint8_t read_count = 0;        // Счетчик измерений
unsigned long lastPressureRead = 0;
unsigned long lastSecondChannelRead = 0;
unsigned long lastBatteryWarning = 0;
bool adsFatalError = false;    // Фатальная ошибка ADS1115 (требует перезагрузки)
bool adsTransientError = false;// Временная ошибка ADS1115 (автовосстановление)
bool lowBatteryMode = false;   // Режим низкого заряда батареи

// Калибровочный (поправочный) коэффициент
const float CALIBRATION_FACTOR = 1.00000; // X.XXXXX формат

// Параметры преобразования напряжения
const float ADC_FULL_SCALE = 4.096;      // Максимальное напряжение в вольтах
const float VOLTAGE_DIVIDER_RATIO = 2.0; // Коэффициент делителя 1:2 на входе АЦП (100кОм/100кОм)

// Пороги напряжения батареи
const float LOW_BATT_THRESHOLD = 3.7;  // 3.7V - предупреждение
const float CRIT_BATT_THRESHOLD = 3.6; // 3.6V - критический уровень

// Класс обработки подключений
class MyServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
        deviceConnected = true;
        Serial.println("Device connected");
    };

    void onDisconnect(BLEServer* pServer) {
        deviceConnected = false;
        Serial.println("Device disconnected");
        digitalWrite(LED_PIN, HIGH); // Выключить светодиод
    }
};

bool readADS1115(int16_t *value, uint16_t config) {
    // Запись конфигурации
    Wire.beginTransmission(ADS_ADDR);
    Wire.write(0x01); // Регистр конфигурации
    Wire.write(highByte(config));
    Wire.write(lowByte(config));
    
    if (Wire.endTransmission() != 0) {
        Serial.println("I2C write error");
        return false;
    }

    // Ожидание преобразования
    delay(2);

    // Чтение результата
    Wire.beginTransmission(ADS_ADDR);
    Wire.write(0x00); // Регистр результата
    
    if (Wire.endTransmission(false) != 0) {
        Serial.println("I2C write error");
        return false;
    }
    
    uint8_t bytesRead = Wire.requestFrom(ADS_ADDR, 2);
    if (bytesRead != 2) {
        Serial.println("I2C read error");
        return false;
    }

    // Сборка 16-битного значения
    uint8_t msb = Wire.read();
    uint8_t lsb = Wire.read();
    *value = (msb << 8) | lsb;
    return true;
}

void disableConverter() {
    digitalWrite(MT3608_EN, LOW);
    pinMode(MT3608_EN, INPUT_PULLDOWN);
    Serial.println("Converter disabled (INPUT_PULLDOWN)");
}

void prepareForDeepSleep() {
    Serial.println("Preparing for deep sleep...");
    
    // Выключение светодиода
    digitalWrite(LED_PIN, HIGH);
    
    // Отключение преобразователя
    disableConverter();
    
    // Отключение I2C
    Wire.end();
    
    // Отключение BLE
    if (pServer) {
        pServer->getAdvertising()->stop();
        BLEDevice::deinit();
    }
    
    // Перевод пинов в безопасное состояние
    pinMode(LED_PIN, INPUT);
    
    // Установка флага сна
    sleepFlag = true;
    
    Serial.println("Entering deep sleep. Turn power OFF and ON to restart.");
    delay(100);
    
    // Переход в глубокий сон
    esp_deep_sleep_start();
}

float convertToVoltage(int16_t adc_value) {
    // Преобразование значения АЦП в напряжение с учетом делителя
    float voltage_adc = (adc_value * ADC_FULL_SCALE) / 32767.0;
    float real_voltage = voltage_adc * VOLTAGE_DIVIDER_RATIO;
    return real_voltage;
}

void checkBatteryVoltage(float voltage) {
    // Проверка критического уровня
    if (voltage < CRIT_BATT_THRESHOLD) {
        Serial.println("CRITICAL battery level! Entering deep sleep.");
        prepareForDeepSleep();
    }
    // Проверка низкого уровня
    else if (voltage < LOW_BATT_THRESHOLD) {
        if (!lowBatteryMode) {
            Serial.println("LOW battery mode activated");
            lowBatteryMode = true;
        }
    }
    // Возврат к нормальному режиму
    else {
        if (lowBatteryMode) {
            Serial.println("Battery voltage normal. Exiting LOW battery mode");
            lowBatteryMode = false;
        }
    }
}

void setup() {
    Serial.begin(115200);
    
    // Вывод коэффициента калибровки
    Serial.print("Calibration factor: ");
    Serial.println(CALIBRATION_FACTOR, 5); // 5 знаков после запятой
    
    // Настройка пинов
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, HIGH); // Выключить светодиод
    
    // Инициализация управления преобразователем
    pinMode(MT3608_EN, OUTPUT);
    digitalWrite(MT3608_EN, HIGH); // Включить преобразователь
    Serial.println("Converter enabled (HIGH)");

    // Инициализация I2C
    Wire.begin(8, 9); // SDA=GPIO8, SCL=GPIO9

    // Проверка наличия ADS1115
    Wire.beginTransmission(ADS_ADDR);
    if (Wire.endTransmission() != 0) {
        Serial.println("ADS1115 not found! Communication error");
        adsFatalError = true;
    } else {
        Serial.println("ADS1115 initialized");
    }

    // Проверка состояния сна
    if (sleepFlag) {
        sleepFlag = false;
        prepareForDeepSleep();
    }

    // Инициализация BLE
    BLEDevice::init("ESP32-Pressure-Low");
    
    // Создание BLE сервера
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new MyServerCallbacks());
    
    // Создание BLE сервиса
    BLEService *pService = pServer->createService(SERVICE_UUID);
    
    // Создание характеристики
    pTempCharacteristic = pService->createCharacteristic(
        TEMP_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pTempCharacteristic->addDescriptor(new BLE2902());

    //Характеристика для передачи напряжения батареи
    batteryCharacteristic = pService->createCharacteristic(
        BATTERY_CHAR_UUID,
        BLECharacteristic::PROPERTY_READ |
        BLECharacteristic::PROPERTY_NOTIFY
    );
    batteryCharacteristic->addDescriptor(new BLE2902());
    
    // Запуск сервиса
    pService->start();
    
    // Начало рассылки
    BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(SERVICE_UUID);
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    BLEDevice::startAdvertising();

    Serial.println("BLE Pressure device active. Waiting for connections...");
    Serial.println("Device will enter deep sleep after 2 hours of operation");
}

void loop() {
    unsigned long currentMillis = millis();
    
    // Управление светодиодом
    if (!deviceConnected) {
        if (currentMillis - previousMillis >= 100) {
            previousMillis = currentMillis;
            ledState = (ledState == LOW) ? HIGH : LOW;
            digitalWrite(LED_PIN, ledState);
        }
    } else {
        if (currentMillis - previousMillis >= 2000) {
            previousMillis = currentMillis;
            digitalWrite(LED_PIN, LOW);
        } else if (currentMillis - previousMillis >= 100) {
            digitalWrite(LED_PIN, HIGH);
        }
    }

    // Проверка таймаута работы
    if (currentMillis >= 7200000) {
        prepareForDeepSleep();
    }

    // Обработка подключений BLE
    if (!deviceConnected && oldDeviceConnected) {
        delay(500);
        pServer->startAdvertising();
        Serial.println("Advertising restarted");
        oldDeviceConnected = deviceConnected;
    }
    if (deviceConnected && !oldDeviceConnected) {
        oldDeviceConnected = deviceConnected;
    }

    // Считывание второго канала (конфигурация F3E3) каждые 5 секунд
    // Осуществляется даже при фатальной ошибке
    if (currentMillis - lastSecondChannelRead >= 1000) {
        lastSecondChannelRead = currentMillis;
        
        int16_t second_channel_value;
        if (readADS1115(&second_channel_value, ADS_CONFIG_CH2)) {
            // Преобразование в напряжение с учетом делителя
            float voltage = convertToVoltage(second_channel_value);
            
            Serial.print("Channel 2 (D3E3) ADC raw: ");
            Serial.print(second_channel_value);
            Serial.print(" -> Battery Voltage: ");
            Serial.print(voltage, 4);
            Serial.println(" V");

            char voltageStr[10];
            dtostrf(voltage, 1, 4, voltageStr);
            batteryCharacteristic->setValue(voltageStr);
            batteryCharacteristic->notify();
            
            // Проверка напряжения батареи
            checkBatteryVoltage(voltage);
        } else {
            Serial.println("Error reading second channel (D3E3)");
        }
    }
    
    // Отправка предупреждения о низком заряде
    if (lowBatteryMode && deviceConnected && (currentMillis - lastBatteryWarning >= 1000)) {
        lastBatteryWarning = currentMillis;
        pTempCharacteristic->setValue("LowBatt");
        pTempCharacteristic->notify();
        Serial.println("Sent LowBatt warning");
    }

    // Обработка фатальной ошибки ADS1115
    if (adsFatalError) {
        if (deviceConnected && (currentMillis - lastPressureRead >= 1000)) {
            lastPressureRead = currentMillis;
            pTempCharacteristic->setValue("Fatal error");
            pTempCharacteristic->notify();
            Serial.println("Sent Fatal Error: ADS1115 communication failure");
        }
        // Продолжаем работу для контроля батареи
        return; // Прекращаем операции с каналом 1, но канал 2 уже обработан
    }

    // Если в режиме низкого заряда - пропускаем обработку давления
    if (lowBatteryMode) {
        return;
    }

    // Проверка необходимости чтения давления
    bool shouldReadPressure = false;

    // Определение необходимости чтения
    if (read_count < 3) {
        shouldReadPressure = (currentMillis - lastPressureRead >= 500);
    } 
    else if (deviceConnected) {
        shouldReadPressure = (currentMillis - lastPressureRead >= 1000);
    }

    // Чтение и обработка данных
    if (shouldReadPressure) {
        lastPressureRead = currentMillis;
        int16_t adc_value;
        bool success = readADS1115(&adc_value, ADS_CONFIG_CH1);
        
        if (!success) {
            adsTransientError = true;
            Serial.println("ADS1115 transient error");
            
            // Обработка фатальной ошибки при третьем считывании
            if (read_count == 2) {
                adsFatalError = true;
                Serial.println("Fatal ADS1115 error during base value setting!");
                
                // Отправка фатальной ошибки
                if (deviceConnected) {
                    pTempCharacteristic->setValue("Fatal error");
                    pTempCharacteristic->notify();
                    Serial.println("Sent Fatal Error: ADS1115 during calibration");
                }
            }
            // Отправка временной ошибки при обычном считывании
            else if (read_count >= 3 && deviceConnected) {
                pTempCharacteristic->setValue("Error");
                pTempCharacteristic->notify();
                Serial.println("Sent Transient Error: ADS1115");
            }
        } 
        else {
            adsTransientError = false;
            Serial.print("Channel 1 ADC raw: ");
            Serial.println(adc_value);

            if (read_count < 2) {
                read_count++;
                Serial.print("Skipped measurement ");
                Serial.println(read_count);
            } 
            else if (read_count == 2) {
                base_value = adc_value;
                read_count++;
                Serial.print("Base value set: ");
                Serial.println(base_value);
            } 
            else {
                // Применение калибровочного коэффициента
                float pressure = (adc_value - base_value) * 0.001 * CALIBRATION_FACTOR;
                char txString[12];
                
                // Форматирование с учетом отрицательных значений
                if (pressure < 0) {
                    snprintf(txString, sizeof(txString), "-%05.3f Bar", -pressure);
                } else {
                    snprintf(txString, sizeof(txString), " %05.3f Bar", pressure);
                }

                // Отправка только при подключении
                if (deviceConnected) {
                    pTempCharacteristic->setValue(txString);
                    pTempCharacteristic->notify();
                    Serial.print("Pressure: ");
                    Serial.print(txString);
                    Serial.print(" (Calib: ");
                    Serial.print(CALIBRATION_FACTOR, 5);
                    Serial.println(")");
                }
            }
        }
    }
    
    delay(10);
}
