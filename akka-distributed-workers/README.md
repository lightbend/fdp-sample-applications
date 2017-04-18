# FDP Sample Application for Integration with Analytics Pipeline

The application uses the distributed master/worker pattern and is implemented using Akka clustering.

To run the application, the recommended way is to run the processes in separate JVMs. The application consists of the following components that can be run together ina single JVM or separate JVMs:

* Workers - they are not part of the cluster but can be used to distribute work by the front end
* Front End - generates work and sends to the master / backend
* Backend / Master - Takes work from front end and distributes amongst workers 

## Steps to run the application

* Start backend (2551 is a seed node for the cluster)

`$ sbt "runMain com.lightbend.fdp.sample.Main 2551"`

* Start another backend (2552 is a seed node for the cluster)

`$ sbt "runMain com.lightbend.fdp.sample.Main 2552"`

* Start frontend (Port numbers should be between 3000 and 3999)

`$ sbt "runMain com.lightbend.fdp.sample.Main 3001"`

* Start workers (Start instances of workers to increase load on the system)

```
$ sbt "runMain com.lightbend.fdp.sample.Main 0"
$ sbt "runMain com.lightbend.fdp.sample.Main 0"
$ sbt "runMain com.lightbend.fdp.sample.Main 0"
```


