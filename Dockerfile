FROM java:8
COPY target/payment-to-order-processor.jar /payment-to-order-processor.jar
CMD ["java", "-Xms16m", "-Xmx32m", "-jar", "/payment-to-order-processor.jar"]