#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <Wire.h>
#include <OneWire.h>
#include <DallasTemperature.h>
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

// Управление питанием DS18B20
const int DS18B20_POWER = 6;   // GPIO6 для управления питанием DS18B20

// Параметры DS18B20
const int ONE_WIRE_BUS = 7;    // GPIO7 для данных DS18B20
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);
bool ds18b20Found = false;     // Флаг наличия датчика

// Параметры ADS1115
const int ADS_ADDR = 0x48;     // Адрес I2C (1001000)
const int ADS_CONFIG_CH2 = 0xD5E3; // Конфигурационный регистр для канала 2 (пин 5)

unsigned long lastTemperatureRead = 0;
unsigned long lastSecondChannelRead = 0;
unsigned long lastBatteryWarning = 0;
bool lowBatteryMode = false;   // Режим низкого заряда батареи

// Параметры преобразования напряжения
const float ADC_FULL_SCALE = 2.048;      // Максимальное напряжение в вольтах
const float VOLTAGE_DIVIDER_RATIO = 4.0; // Коэффициент делителя 1:4 на входе АЦП

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
        digitalWrite(LED_PIN, LOW); // Выключить светодиод (LOW = выкл)
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

float readDS18B20Temperature() {
    sensors.requestTemperatures();
    float temperature = sensors.getTempCByIndex(0);
    
    // Проверка на ошибку чтения
    if (temperature == DEVICE_DISCONNECTED_C) {
        Serial.println("Error reading DS18B20");
        return -999.9; // Значение ошибки
    }
    
    return temperature;
}

void disableDS18B20() {
    digitalWrite(DS18B20_POWER, LOW);
    pinMode(DS18B20_POWER, INPUT_PULLDOWN);
    Serial.println("DS18B20 power disabled (INPUT_PULLDOWN)");
}

void prepareForDeepSleep() {
    Serial.println("Preparing for deep sleep...");
    
    // Выключение светодиода
    digitalWrite(LED_PIN, LOW); // LOW = выкл
    
    // Отключение питания DS18B20
    disableDS18B20();
    
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
    
    // Настройка пинов
    pinMode(LED_PIN, OUTPUT);
    digitalWrite(LED_PIN, LOW); // Выключить светодиод (LOW = выкл)
    
    // Инициализация питания DS18B20
    pinMode(DS18B20_POWER, OUTPUT);
    digitalWrite(DS18B20_POWER, HIGH); // Включить питание DS18B20
    Serial.println("DS18B20 power enabled (HIGH)");

    // Инициализация DS18B20
    sensors.begin();
    
    // Проверка наличия DS18B20
    int deviceCount = sensors.getDeviceCount();
    if (deviceCount == 0) {
        Serial.println("DS18B20 not found!");
        ds18b20Found = false;
    } else {
        Serial.print("DS18B20 initialized. Found ");
        Serial.print(deviceCount);
        Serial.println(" sensors");
        ds18b20Found = true;
    }

    // Инициализация I2C
    Wire.begin(8, 9); // SDA=GPIO8, SCL=GPIO9

    // Проверка наличия ADS1115
    Wire.beginTransmission(ADS_ADDR);
    if (Wire.endTransmission() != 0) {
        Serial.println("ADS1115 not found! Communication error");
    } else {
        Serial.println("ADS1115 initialized");
    }

    // Проверка состояния сна
    if (sleepFlag) {
        sleepFlag = false;
        prepareForDeepSleep();
    }

    // Инициализация BLE
    BLEDevice::init("ESP32-Temp-All");
    
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

    Serial.println("BLE Temperature device active. Waiting for connections...");
    Serial.println("Device will enter deep sleep after 2 hours of operation");
}

String formatTemperature(float temperature) {
    String result;
    
    if (temperature >= 100.0) {
        // 3 цифры до точки: "123.4°C"
        result = String(temperature, 1) + "°C";
    } else if (temperature >= 10.0) {
        // 2 цифры до точки: " 12.3°C"
        result = " " + String(temperature, 1) + "°C";
    } else if (temperature >= 0.0) {
        // 1 цифра до точки: "  1.2°C"
        result = "  " + String(temperature, 1) + "°C";
    } else if (temperature > -10.0) {
        // отрицательная, 1 цифра до точки: " -1.2°C"
        result = String(temperature, 1) + "°C";
    } else if (temperature > -100.0) {
        // отрицательная, 2 цифры до точки: "-12.3°C"
        result = String(temperature, 1) + "°C";
    } else {
        // отрицательная, 3 цифры до точки: "-123.4°C"
        result = String(temperature, 1) + "°C";
    }
    
    return result;
}

void loop() {
    unsigned long currentMillis = millis();
    
    // Управление светодиодом при подключении к VCC
    if (!deviceConnected) {
        // Режим ожидания - быстрое мигание
        if (currentMillis - previousMillis >= 100) {
            previousMillis = currentMillis;
            ledState = (ledState == LOW) ? HIGH : LOW;
            digitalWrite(LED_PIN, ledState); // LOW = выкл, HIGH = вкл
        }
    } else {
        // Режим подключения - короткие вспышки каждые 2 секунды
        if (currentMillis - previousMillis >= 2000) {
            previousMillis = currentMillis;
            digitalWrite(LED_PIN, HIGH); // Включить на мгновение
        } else if (currentMillis - previousMillis >= 100) {
            digitalWrite(LED_PIN, LOW); // Выключить
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

    // Считывание второго канала (напряжение батареи) каждые 5 секунд
    if (currentMillis - lastSecondChannelRead >= 1000) {
        lastSecondChannelRead = currentMillis;
        
        int16_t second_channel_value;
        if (readADS1115(&second_channel_value, ADS_CONFIG_CH2)) {
            // Преобразование в напряжение с учетом делителя
            float voltage = convertToVoltage(second_channel_value);
            
            Serial.print("Channel 2 (D5E3) ADC raw: ");
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
            Serial.println("Error reading second channel (D5E3)");
        }
    }
    
    // Отправка предупреждения о низком заряде
    if (lowBatteryMode && deviceConnected && (currentMillis - lastBatteryWarning >= 1000)) {
        lastBatteryWarning = currentMillis;
        pTempCharacteristic->setValue("LowBatt");
        pTempCharacteristic->notify();
        Serial.println("Sent LowBatt warning");
    }

    // Если в режиме низкого заряда - пропускаем обработку температуры
    if (lowBatteryMode) {
        return;
    }

    // Чтение температуры с тем же интервалом, что и давление (каждую секунду при подключении)
    bool shouldReadTemperature = false;

    if (deviceConnected) {
        shouldReadTemperature = (currentMillis - lastTemperatureRead >= 1000);
    }

    // Чтение и обработка температуры
    if (shouldReadTemperature) {
        lastTemperatureRead = currentMillis;
        
        // Если датчик не найден при старте, сразу отправляем ошибку
        if (!ds18b20Found) {
            if (deviceConnected) {
                pTempCharacteristic->setValue("Error sensor");
                pTempCharacteristic->notify();
                Serial.println("Sent Error sensor: DS18B20 not found");
            }
            return;
        }
        
        float temperature = readDS18B20Temperature();
        
        if (temperature != -999.9) { // Успешное чтение
            String tempString = formatTemperature(temperature);

            // Отправка только при подключении
            if (deviceConnected) {
                pTempCharacteristic->setValue(tempString.c_str());
                pTempCharacteristic->notify();
                Serial.print("Temperature: ");
                Serial.println(tempString);
            }
        } else {
            // Ошибка чтения датчика - отправляем "Error sensor"
            if (deviceConnected) {
                pTempCharacteristic->setValue("Error sensor");
                pTempCharacteristic->notify();
                Serial.println("Sent Error sensor: DS18B20 connection problem");
            }
        }
    }
    
    delay(10);
}