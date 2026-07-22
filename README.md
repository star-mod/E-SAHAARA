# E-Sahaara Connect

E-Sahaara Connect is a Java-based web application that bridges the gap between NGOs, orphanages, donors, volunteers, and beneficiaries. It provides a simple platform to request assistance, manage donations, and support social welfare initiatives.

## Features

- User Registration & Login
- NGO Dashboard
- Donor Dashboard
- Beneficiary Support Requests
- Donation Management
- Volunteer Registration
- Simple Web Interface
- File-based Data Storage
- Lightweight HTTP Server (No external frameworks)

## Technologies Used

- Java
- Java HttpServer (`com.sun.net.httpserver.HttpServer`)
- HTML
- CSS
- Java Collections
- File I/O

## Project Structure

```
E-SAHAARA/
│── ESAHAARA.java
│── Dockerfile
│── README.md
```

## Requirements

- Java JDK 21 or later

## Running Locally

Compile the project:

```bash
javac ESAHAARA.java
```

Run the application:

```bash
java ESAHAARA
```

Open your browser:

```
http://localhost:8085
```

> If deployed on Render, the application automatically uses the `PORT` environment variable.

## Docker

Build the Docker image:

```bash
docker build -t e-sahaara .
```

Run the container:

```bash
docker run -p 8085:8085 e-sahaara
```

## Deployment

This project can be deployed on:

- Render
- Railway
- Koyeb
- Fly.io
- Oracle Cloud

## Future Enhancements

- Database integration (MySQL/PostgreSQL)
- Secure password hashing
- Email notifications
- Payment gateway integration
- Admin analytics dashboard
- Mobile application
- Cloud file storage

## Author

**Anupam Kumar Jha**

B.E. Information Science & Engineering  
Bangalore Institute of Technology

## License

This project is created for educational and learning purposes.
