# Sangria pekko-http Library

[![License](http://img.shields.io/:license-Apache%202-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)


A reference, batteries included, [Sangria](https://github.com/sangria-graphql/sangria) [GraphQL](https://graphql.org) 
server implementation using [pekko-http](https://github.com/apache/pekko-http).

This is a fork of [sangria-akka-http](https://github.com/sangria-graphql/sangria-akka-http) that replaces the Akka dependency with Pekko.

Various json libraries are supported:
- [circe](https://github.com/circe/circe)
- ???
- PRs adding the json library of your choice welcome

## Getting started

Add this to your dependencies:
```sbt
libraryDependencies += "com.mosaicpower" %% "sangria-pekko-http-core" % "0.5.0"
// And choose your desired json library support:
libraryDependencies += "com.mosaicpower" %% "sangria-pekko-http-circe" % "0.5.0"
```

*Note*: as of now, no artifact has yet been published.

For a full example, consult the [Sangria Akka Http example project](https://github.com/sangria-graphql/sangria-akka-http-example).
 
## Building
TODO: write

## Contributing
TODO: write
