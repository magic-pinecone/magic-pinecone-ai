# Builds the wasmJs preview of the shared Compose UI and serves it as static files.
# The Gradle daemon asks for Amazon Corretto 21 (gradle/gradle-daemon-jvm.properties).
FROM amazoncorretto:21-al2023 AS build
# gradlew needs xargs, which the base image leaves out.
RUN dnf install -y findutils && dnf clean all
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :shared:wasmJsBrowserDistribution

FROM nginx:1.27-alpine
COPY --from=build /src/shared/build/dist/wasmJs/productionExecutable /usr/share/nginx/html
