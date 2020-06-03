FROM openjdk:8-jre
COPY target/payment-to-order-processor.jar /payment-to-order-processor.jar
CMD ["java", "-Xms16m", "-Xmx32m", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-jar", "/payment-to-order-processor.jar"]