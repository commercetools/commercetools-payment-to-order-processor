FROM amazoncorretto:8-alpine-jre
COPY target/payment-to-order-processor.jar /payment-to-order-processor.jar
CMD ["java", "-Xmx256m", "-jar", "/payment-to-order-processor.jar"]
