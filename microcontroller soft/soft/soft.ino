#include <SoftwareSerial.h>
#include <NewPing.h>

#define BluetoothPacketMaxSize 24

#define UltrasonicTrigger 10
#define UltrasonicEcho 11
#define UltrasonicMaxDistance 400

#define BluetoothRX 8
#define BluetoothTX 9

SoftwareSerial bluetooth(BluetoothRX, BluetoothTX);
NewPing ultrasonic(UltrasonicTrigger, UltrasonicEcho, UltrasonicMaxDistance);

struct MoveParams
{
    char direction;
    int distance;
    int speed;
};

class Sliced
{
public:
    Sliced(int expected)
    {
        size = expected;
        data = new char[size * 5];
        block = nullptr;
    }

    char* index(int x, int y)
    {
        return &data[x * 5 + y];
    }

    char* memoryBlock(int x)
    {
        if (block != nullptr) { delete block; block = nullptr; }
        block = new char[size];
        memcpy(block, index(x, 0), size);
        return block;
    }

    ~Sliced()
    {
        if (block != nullptr) { delete block; block = nullptr; }
        delete[] data;
    }

private:
    int size;
    char* data;
    char* block;
};

Sliced sliceMessage(char str[], int expectedCount)
{
    Sliced data(expectedCount);

    char divider[] = ";";
    char* buffer;
    buffer = strtok(str, divider);
    int counter = 0;
    while (buffer != NULL)
    {
        strncpy(data.index(counter++, 0), buffer, 5);
        buffer = strtok(NULL, divider);
    }

    return data;
}

class Motor
{
public:

    enum DIR { CLOCKWISE, COUNTERCLOCKWISE };

    Motor() {}
    Motor(int speedPin, int input1Pin, int input2Pin)
    {
        this->speedPin = speedPin;
        this->input1Pin = input1Pin;
        this->input2Pin = input2Pin;

        pinMode(speedPin, OUTPUT);
        pinMode(input1Pin, OUTPUT);
        pinMode(input2Pin, OUTPUT);
    }

    void setRotatingDirection(DIR dir)
    {
        if (dir == CLOCKWISE) { digitalWrite(input1Pin, LOW); digitalWrite(input2Pin, HIGH); }
        else { digitalWrite(input1Pin, HIGH); digitalWrite(input2Pin, LOW); }
    }

    void rotate(unsigned char pwm)
    {
        analogWrite(speedPin, pwm);
    }

    // Suddenly stops both wheels with counterflow current and after 'forTime'
    // releases wheels.
    void stop(int forTime = 20)
    {
        digitalWrite(input1Pin, HIGH);
        digitalWrite(input2Pin, HIGH);

        delay(forTime);

        digitalWrite(input1Pin, LOW);
        digitalWrite(input2Pin, LOW);
    }

private:

    int speedPin;
    int input1Pin;
    int input2Pin;
};

Motor leftMotor, rightMotor;

void setup()
{
    // setup bluetooth communication
    bluetooth.begin(9600);

    // setup motors
    leftMotor = Motor(5, 4, 7);
    rightMotor = Motor(6, 13, 12);
}

void loop()
{
    // check if there is any incoming command from mobile app
    if (bluetooth.available() > 0)
    {
        // reading whole message that mobile app sent
        // app sends message in following order: [type-letter][value];[value];[value]...
        char typeLetter = bluetooth.read();

        char message[BluetoothPacketMaxSize];
        memset(message, 0, BluetoothPacketMaxSize);
        size_t readCount = bluetooth.readBytesUntil('\n', message, BluetoothPacketMaxSize);

        if(typeLetter == 'M') // M - move
        {
            // get params from message
            MoveParams params = parseMoveParams(message);

            switch (params.direction)
            {
                case 'T': // T - towards
                    goTowards(params.distance, params.speed);
                    break;

                case 'B': // B - backwards
                    goBackwards(params.distance, params.speed);
                    break;

                case 'R': // R - rightwards
                    goRightwards(params.distance, params.speed);
                    break;

                case 'L': // L - leftwards
                    goLeftwards(params.distance, params.speed);
                    break;
            }

        }
        else if (typeLetter == 'D')
        {
            // In this case, we can receive one byte extra saying
            // which dance should it be.
            // There are serval dance types available.
            char danceType = message[0];
            dance(danceType);
        }

        // Send feedback message that requested command is completed.
        bluetooth.println("F");
    }

    delay(10);
}

MoveParams parseMoveParams(char message[])
{
    Sliced data = sliceMessage(message, 3);

    MoveParams retObj;
    retObj.direction = *(data.index(0, 0));
    retObj.distance =  atoi(data.memoryBlock(1));
    retObj.speed =     atoi(data.memoryBlock(2));

    return retObj;
}

// *****************************************************************************
// In this part we will have to calculate time needed to complete given movement
// Robot travels approx 16 cm/s.
// -----------------------------------------------------------------------------

// returns true if any obstacle is at least 'distance' cm from the Mobi's face
bool obstacleWithin(int distance)
{
    int centimeters = ultrasonic.ping_cm();
    return centimeters == 0 ? false : centimeters <= distance;
}

void goTowards(long distance, unsigned char speed)
{
    move(distance, speed);
}

void goBackwards(long distance, unsigned char speed)
{
    turn(PI, speed);
    move(distance, speed);
}

void goRightwards(long distance, unsigned char speed)
{
    turn(HALF_PI, speed);
    move(distance, speed);
}

void goLeftwards(long distance, unsigned char speed)
{
    turn(-HALF_PI, speed);
    move(distance, speed);
}

void turn(float angle, unsigned char speed)
{
    if (angle == 0) { return; }

    else if (angle < 0) // will turn left
    {
        rightMotor.setRotatingDirection(Motor::DIR::CLOCKWISE);
        leftMotor.setRotatingDirection(Motor::DIR::CLOCKWISE);
        angle = -angle; // get this back to positive side,
                        // otherwise further calculations won't succeed.
    }
    else if (angle > 0) // will turn right
    {
        rightMotor.setRotatingDirection(Motor::DIR::COUNTERCLOCKWISE);
        leftMotor.setRotatingDirection(Motor::DIR::COUNTERCLOCKWISE);
    }

    // Variable that will hold value of time needed to complete given movement.
    float seconds;

    // At first make any eventual rotation.
    seconds = timeRobotNeedsToTurn(angle, speed);

    leftMotor.rotate(speed);
    rightMotor.rotate(speed);

    delay(seconds * 1000);

    // Stop wheels.
    leftMotor.stop();
    rightMotor.stop();
}

void move(long distance, unsigned char speed)
{
    if (distance == 0) { return; }

    else if (distance < 0) // will go backwards
    {
        rightMotor.setRotatingDirection(Motor::DIR::COUNTERCLOCKWISE);
        leftMotor.setRotatingDirection(Motor::DIR::CLOCKWISE);
    }
    else if (distance > 0) // will go towards
    {
        rightMotor.setRotatingDirection(Motor::DIR::CLOCKWISE);
        leftMotor.setRotatingDirection(Motor::DIR::COUNTERCLOCKWISE);
    }

    // Variable that will hold value of time needed to complete given movement.
    float milliseconds;

    // At first make any eventual rotation.
    milliseconds = timeRobotNeedsToTravel(distance, speed) * 1000;

    leftMotor.rotate(speed);
    rightMotor.rotate(speed);

    // Async delay which includes checking for obstacles with ultrasonic sensor.
    unsigned long previousTime = millis();

    while (millis() - previousTime < milliseconds && !obstacleWithin(15))
    {
        // Saving some computional power with this delay.
        delay(10);
    }

    // Stop wheels.
    leftMotor.stop();
    rightMotor.stop();
}

float timeRobotNeedsToTravel(long distance, unsigned char speed)
{
    return (distance / 16.f) / (speed / 255.f);
}

float timeRobotNeedsToTurn(float angle, unsigned char speed)
{
    // how long will it take to overcome arc length
    float arcLength = angle * 9.5; // angle in radians * circle radius
    return timeRobotNeedsToTravel(arcLength, speed);
}

// -----------------------------------------------------------------------------
// *****************************************************************************


// *****************************************************************************
// Part dedicated to robot dances.
// -----------------------------------------------------------------------------

void dance(char danceType)
{
    unsigned char speed = 255;

    switch (danceType)
    {
        case '1':
            // This dance consists of slight movement to the left,
            // slight to the right, again to the left and one revolution.
            turn(-HALF_PI / 2.f, speed);
            turn(HALF_PI, speed);
            turn(-HALF_PI, speed);
            turn(TWO_PI + HALF_PI / 2.f, speed);
            break;

        case '2':

            break;

        case '3':

            break;
    }
}

// -----------------------------------------------------------------------------
// *****************************************************************************
