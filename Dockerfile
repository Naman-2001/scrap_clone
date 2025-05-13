
FROM azul/zulu-openjdk-alpine:24-latest as build
# Set the working directory inside the container
WORKDIR /app

# Copy the Maven wrapper executable
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Copy the Maven settings file
COPY settings.xml /root/.m2/settings.xml

# Make mvnw executable
RUN chmod +x ./mvnw

# Download dependencies and cache them
RUN ./mvnw dependency:go-offline --settings /root/.m2/settings.xml

# Copy the rest of the source code
COPY src ./src

# Build the application
RUN ./mvnw clean package -DskipTests --settings /root/.m2/settings.xml

# Stage 2: Create the final image
FROM azul/zulu-openjdk-alpine:24-latest

# Create a group and user to run the application
RUN addgroup -S spring && adduser -S spring -G spring

# Create directories for the application and set permissions
RUN mkdir -p /app && chown spring:spring /app

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Change the ownership of the JAR file to the non-root user
RUN chown spring:spring app.jar

# Switch to the non-root user
USER spring:spring

# Expose the port the application runs on
EXPOSE 8080

# Set the entry point to run the application
ENTRYPOINT ["java", "-jar", "app.jar"]