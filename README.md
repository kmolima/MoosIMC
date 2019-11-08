# MoosIMC
Simple Java connector for exposing MOOS vehicles to IMC

# MoosIMC - version 2.0
Porting some Moos IVP functionalities into IMC, enabling the control from Neptus Command and Control Unit.
This repository uses the java version of MOOS: https://github.com/moos-ivp/java-moos and the java binding for the IMC protocol: https://github.com/LSTS/imcjava .
For more information visit: https://lsts.pt/


# Compiling
Use Apache ANT for generating an executable jar:

```ant```

# Running
```java -jar MoosAdapter.jar <vehicle> <imc_id> <moos_hostname> <moos_port>```

Example:
```java -jar MoosAdapter.jar caravela 0x0802 localhost 9000```

# Controlling Moos vehicles from Neptus
Use the FollowReference plugin to control the vehicle.
The waypoint behaviour must be defined and has to be enabled by the variable follow_neptus.

