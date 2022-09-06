FROM amazoncorretto:8-alpine-jre
COPY target/payment-to-order-processor.jar /payment-to-order-processor.jar
CMD ["java", "-Xms16m", "-Xmx32m", "-jar", "/payment-to-order-processor.jar"]