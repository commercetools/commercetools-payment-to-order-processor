FROM eclipse-temurin:11-alpine
COPY target/payment-to-order-processor.jar /payment-to-order-processor.jar
CMD ["java", "-Xmx256m", "-jar", "/payment-to-order-processor.jar"]
