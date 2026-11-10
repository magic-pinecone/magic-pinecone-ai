# Builds the wasmJs preview of the shared Compose UI and serves it as static files.
# The Gradle daemon asks for Amazon Corretto 21 (gradle/gradle-daemon-jvm.properties).
FROM amazoncorretto:21-al2023 AS build
# gradlew needs xargs and the Node.js that Kotlin/Wasm downloads needs libatomic; the base image has neither.
RUN dnf install -y findutils libatomic && dnf clean all
WORKDIR /src
COPY . .
# sharing=locked: Gradle cannot share its home between two builds that run at once. With the default
# (shared) the second build gave up after a minute waiting for the journal cache lock; now it waits.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    # One 2 GB build JVM with the Kotlin compiler inside it, instead of gradle.properties' 4 GB Gradle
    # plus 3 GB Kotlin daemon: the deploy server ran out of memory when two previews built at once.
    ./gradlew --no-daemon \
      -Dorg.gradle.jvmargs="-Xmx2g -Dfile.encoding=UTF-8" \
      -Pkotlin.compiler.execution.strategy=in-process \
      :shared:wasmJsBrowserDistribution

FROM nginx:1.27-alpine
# Serves the static site and proxies /api/ to the api service, so the browser stays same-origin.
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
COPY --from=build /src/shared/build/dist/wasmJs/productionExecutable /usr/share/nginx/html
