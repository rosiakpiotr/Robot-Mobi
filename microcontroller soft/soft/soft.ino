#include <SoftwareSerial.h>
#include <NewPing.h>

#define BluetoothPacketMaxSize 24

#define UltrasonicTrigger 10
#define UltrasonicEcho 11

#define BluetoothRX 8
#define BluetoothTX 9

SoftwareSerial bluetooth(BluetoothRX, BluetoothTX);
NewPing ultrasonic(UltrasonicTrigger, UltrasonicEcho);

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
        // app sends message in following order: [type-letter][id][value];[id][value];[id][value]...
        char typeLetter = bluetooth.read();

        char message[BluetoothPacketMaxSize];
        memset(message, 0, BluetoothPacketMaxSize);
        size_t readCount = bluetooth.readBytesUntil('\n', message, BluetoothPacketMaxSize);

        if (typeLetter == 'M') // M - move
        {
            // get params from message
            MoveParams params = parseMoveParams(message);

            switch (params.direction)
            {
                case 'T': // T - towards
                    bluetooth.print("Towards ");
                    bluetooth.println(params.distance);
                    goTowards(params.distance, params.speed);
                    break;

                case 'B': // B - backwards
                    bluetooth.print("Back ");
                    bluetooth.println(params.distance);
                    goBackwards(params.distance, params.speed);
                    break;

                case 'R': // R - rightwards
                    bluetooth.print("Right ");
                    bluetooth.println(params.distance);
                    goRightwards(params.distance, params.speed);
                    break;

                case 'L': // L - leftwards
                    bluetooth.print("Left");
                    bluetooth.println(params.distance);
                    goLeftwards(params.distance, params.speed);
                    break;
            }

            // Set motors speed to 0 = stop them
            move(0);

            bluetooth.println("Finish");
        }
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
    return ultrasonic.ping_cm() <= distance;
}

void goTowards(long distance, unsigned char speed)
{
    rightMotor.setRotatingDirection(Motor::DIR::CLOCKWISE);
    leftMotor.setRotatingDirection(Motor::DIR::COUNTERCLOCKWISE);

    move(speed);

    float seconds = timeRobotNeedsToTravel(distance, speed);

    // controlling distance to nearest object to the front of the robot
    // during the moving time
    long startTime = millis();
    while (((millis() - startTime) >= (seconds * 1000)) && !obstacleWithin(15))
    { delay(10); }
}

void goBackwards(long distance, unsigned char speed)
{
    rightMotor.setRotatingDirection(Motor::DIR::CLOCKWISE);
    leftMotor.setRotatingDirection(Motor::DIR::CLOCKWISE);

    move(speed);

    float seconds = timeRobotNeedsToTurn(M_PI, speed);
    delay(seconds * 1000);

    goTowards(distance, speed);
}

void goRightwards(long distance, unsigned char speed)
{
    // turn robot to the right and go straight
    rightMotor.setRotatingDirection(Motor::DIR::COUNTERCLOCKWISE);
    leftMotor.setRotatingDirection(Motor::DIR::COUNTERCLOCKWISE);

    move(speed);

    float seconds = timeRobotNeedsToTurn(HALF_PI, speed);
    delay(seconds * 1000);

    goTowards(distance, speed);
}

void goLeftwards(long distance, unsigned char speed)
{
    // turn robot to the left and go straight
    rightMotor.setRotatingDirection(Motor::DIR::CLOCKWISE);
    leftMotor.setRotatingDirection(Motor::DIR::CLOCKWISE);

    move(speed);

    float seconds = timeRobotNeedsToTurn(HALF_PI, speed);
    delay(seconds * 1000);

    goTowards(distance, speed);
}

void move(unsigned char speed)
{
    rightMotor.rotate(speed);
    leftMotor.rotate(speed);
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
