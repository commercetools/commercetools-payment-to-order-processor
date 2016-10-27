FROM java:8
COPY ${project.build.finalName}.jar /application.jar
EXPOSE 8080
CMD ["java", "-Xms16m", "-Xmx32m", "-jar", "/application.jar"]