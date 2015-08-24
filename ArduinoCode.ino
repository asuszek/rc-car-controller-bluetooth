/*
  This code is for the Arduino mounted to the RC car. Pins 6, 7, 8 (PWM), and 9 (PWM) 
  are digital pins connected to the RC car's dual H-bridge chip (wired to the streering servo
  and drive motor). Commands are recieved via bluetooth serial connection with accompanying
  Android code.
*/

#define forwardPin  10
#define backwardPin 9
#define leftPin     6
#define rightPin    7

int velocity = 0;
int flag = 0;

void setup() {
  pinMode(forwardPin, OUTPUT);
  pinMode(backwardPin, OUTPUT);
  pinMode(leftPin, OUTPUT);
  pinMode(rightPin, OUTPUT);
  
  Serial.begin(9600); //Default connection rate for BT module
  Serial.setTimeout(700); //Wait maximum of 1 second.
}

void loop(){
  //wait until connection is established with bluetooth transmitter
  while(!(Serial.available() > 0)){
    Serial.flush();
  }

  velocity = (int)Serial.read();
  flag = 0;
 
  if (velocity >= 126  && velocity <= 251) 
    moveForward();
  else if(velocity == 125) 
    stop();
  else if (velocity >= 1 && velocity <= 124) 
    moveBackward();
  else if (velocity == 253) 
    turnLeft();
  else if (velocity == 254) 
    center();
  else if (velocity == 255) 
    turnRight();
  else if (velocity == 252) 
    stop();
  else {
   stop();
   Serial.println("Invalid speed");
  }
}

void moveForward(){
  digitalWrite(backwardPin,LOW);
  analogWrite(forwardPin, 2*(velocity-125));
   if (flag == 0){
     Serial.print("Going forward. Speed: ");
     Serial.println(velocity);
     flag = 1;
  }
}

void moveBackward(){
  digitalWrite(forwardPin, LOW);
  analogWrite(backwardPin, 2*(125-velocity));
   if (flag == 0){
     Serial.print("Going backward. Speed: ");
     Serial.println(velocity);
     flag = 1;
  }
}

void stop(){
  digitalWrite(forwardPin,LOW);  
  digitalWrite(backwardPin,LOW);
  Serial.println("Stopped");
}

void turnLeft(){
  digitalWrite(rightPin,LOW);
  digitalWrite(leftPin,HIGH);
  Serial.println("Turning left");
}

void turnRight(){
  digitalWrite(rightPin,HIGH);
  digitalWrite(leftPin,LOW);
  Serial.println("Turning right");
}

void center(){
  digitalWrite(rightPin,LOW);
  digitalWrite(leftPin,LOW);
  Serial.println("Centered");
}
