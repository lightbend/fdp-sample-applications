# FDP Sample Applications

Contains sample applications for Lightbend Fast Data Platform (FDP).

The applications are organized in subdirectories. For details, see the corresponding READMEs:

* [nwintrusion/README.md](https://github.com/typesafehub/fdp-sample-apps/blob/master/nwintrusion/README.md)
* [bigdl/README.md](https://github.com/typesafehub/fdp-sample-apps/blob/master/bigdl/README.md)
* [flink/README.md](https://github.com/typesafehub/fdp-sample-apps/blob/master/flink/README.md)
* [kstream/README.md](https://github.com/typesafehub/fdp-sample-apps/blob/develop/kstream/README.md)

## A note about versioning

Don't put a `version := ...` setting in your sub-project because versioning is completely
controlled by [`sbt-dynver`](https://github.com/dwijnand/sbt-dynver) and enforced by the `Enforcer` plugin found in the `build-plugin`
directory.