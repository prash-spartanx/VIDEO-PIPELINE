# -------------------- STAGE 1: The "Build" Stage --------------------
# This stage builds your Java application.
# We name it 'build' using "AS build"
FROM eclipse-temurin:21-jdk-alpine AS build

# Set the working directory
WORKDIR /app

# Copy Maven files
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Download dependencies
RUN ./mvnw dependency:go-offline

# Copy all source code
COPY src ./src

# Remove the test folder (as you were doing)
RUN rm -rf src/test

# Package the application, skipping tests
RUN ./mvnw package -DskipTests

# At this point, /app/target/video-synthesis-service-0.0.1-SNAPSHOT.jar exists.
# Nothing from this stage will be in the final image *except* what we copy.

# -------------------- STAGE 2: The "Runtime" Stage --------------------
# This is the final, small image that will actually run.
# We use 'jre' (Java Runtime Environment) instead of 'jdk' because it's smaller.
FROM eclipse-temurin:21-jre-alpine

# Set the working directory
WORKDIR /app

# --- This is the new part for Alpine/Chrome ---
# 1. Install Chromium, ChromeDriver, and necessary dependencies.
#    'chromium-chromedriver' is packaged with 'chromium' in Alpine
#    and guarantees the versions will match.
RUN apk update && apk add --no-cache \
    chromium \
    chromium-chromedriver \
    xvfb \
    ttf-freefont \
    nss \
    dbus \
    udev

# 2. Set the Environment Variables for your Java code to find the binaries.
ENV CHROME_BIN=/usr/bin/chromium-browser
ENV CHROMEDRIVER_DIR=/usr/bin
# --- End of new part ---

# Expose the application port
EXPOSE 8080

# Copy *only* the built JAR file from the 'build' stage into this final image
COPY --from=build /app/target/video-synthesis-service-0.0.1-SNAPSHOT.jar ./app.jar

# The command to run the application
# We use 'app.jar' for a cleaner path
ENTRYPOINT ["java", "-jar", "app.jar"]
