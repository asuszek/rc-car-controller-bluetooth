# rc-car-controller-bluetooth
An Android app to control a modified RC car via gyroscope using Bluetooth and an Arduino

This app was created at a UMass hackathon. It is lightweight and simple (completed in 24 hours),
but is powerful and fully functioning.

The app consists of a single activity which provides two modes: normal and gyroscopic. Normal mode
uses buttons for drive and reverse and the gyroscope for turning (to simulate a steering wheel). 
Gyroscopic mode removes the buttons and so forward motion is provided by the gyroscope (simulating
a joystick). Braking in both modes is done incrementally to avoid sudden stopping and back EMF in 
the car's motor.

Transmission over Bluetooth to the Arduino mounted on the car is all done concurrently and is completely
thread safe.

Note: The app depends on a modified RC car (modified with an Arduino and Bluetooth Receiver), 
and also simple C code to be run on the Arduino which is not provided here. 

This project was designed and built for fun, and therefore does not provide a thorough API to be used
universally. However, I encourage anyone to use this code as a basis for their own controller so that 
you can focus on adding extra functionality.
