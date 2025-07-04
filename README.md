# InGrid-API

## Development

Test 
Just run `Application.kt` in your IntelliJ-IDE or call the following gradle command:

```shell
./gradlew -t run
```

For code styling the `ktfmt`-plugin is used. This can be added to IntelliJ IDE and must be activated in the settings afterward.

## Configuration

To configure your project you can edit the file: `src/main/resources/application.yaml`. There you can control which modules shall be used.

## Release

Create an annotated tag with the release version in the main or support branch, to create a release version.

## Further documentation

* [Ktor](https://ktor.io/docs)
* [Ktor Swagger-UI](https://github.com/SMILEY4/ktor-swagger-ui)
* * [Koin](https://insert-koin.io/)
