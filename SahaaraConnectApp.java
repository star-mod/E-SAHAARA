import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
 
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.io.Serializable;
 
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
 
public class SahaaraConnectApp {
    public static void main(String[] args) throws Exception {
        AppState state = AppState.loadOrNew(new File("sahaara_connect.data"));
        HttpServer server = HttpServer.create(new InetSocketAddress(8085), 0);
        server.setExecutor(Executors.newCachedThreadPool());
 
        Router r = new Router(state);
 
        server.createContext("/", r::home);
        server.createContext("/organizations", r::organizations);
        server.createContext("/organizations/create", r::orgCreate);
        server.createContext("/organizations/delete", r::orgDelete);
        server.createContext("/needs", r::needs);
        server.createContext("/needs/create", r::needCreate);
        server.createContext("/needs/delete", r::needDelete);
        server.createContext("/donate", r::donate);
        server.createContext("/donate/checkout", r::donateCheckout);
        server.createContext("/healthcare", r::healthcare);
        server.createContext("/healthcare/create", r::healthCreate);
        server.createContext("/patients", r::patients);
        server.createContext("/patients/create", r::patientCreate);
        server.createContext("/patients/delete", r::patientDelete);
        server.createContext("/static/app.css", Router::serveCss);
        server.createContext("/static/app.js", Router::serveJs);
        server.createContext("/pay/processing", r::payProcessing);
        server.createContext("/receipt", r::receiptPage);
        server.createContext("/tax-benefits", r::taxBenefits);
 
        server.start();
        System.out.println("========================================");
        System.out.println("  Sahaara Connect is running!");
        System.out.println("  Open in browser: http://localhost:8085");
        System.out.println("========================================");
    }
}
 
// ===== Data models & persistence =====
class AppState implements Serializable {
    public Map<Long, Organization> orgs = new LinkedHashMap<>();
    public Map<Long, Need> needs = new LinkedHashMap<>();
    public Map<Long, Donation> donations = new LinkedHashMap<>();
    public Map<Long, HealthcareFacility> facilities = new LinkedHashMap<>();
    public Map<Long, PatientRecord> patientRecords = new LinkedHashMap<>();
 
    public long orgSeq = 1;
    public long needSeq = 1;
    public long donationSeq = 1;
    public long facilitySeq = 1;
    public long patientSeq = 1;
 
    public synchronized Organization addOrg(Organization o) {
        o.id = orgSeq++;
        orgs.put(o.id, o);
        save();
        return o;
    }
    public synchronized Need addNeed(Need n) {
        n.id = needSeq++;
        if (n.amountRaised == null) n.amountRaised = 0.0;
        needs.put(n.id, n);
        save();
        return n;
    }
    public synchronized Donation addDonation(Donation d) {
        d.id = donationSeq++;
        d.donationDate = new Date();
        donations.put(d.id, d);
        Need n = needs.get(d.needId);
        if (n != null && d.amount != null) {
            if (n.amountRaised == null) n.amountRaised = 0.0;
            n.amountRaised += d.amount;
        }
        save();
        return d;
    }
    public synchronized HealthcareFacility addFacility(HealthcareFacility h) {
        h.id = facilitySeq++;
        h.lastUpdated = new Date();
        facilities.put(h.id, h);
        save();
        return h;
    }
    public synchronized PatientRecord addPatientRecord(PatientRecord p) {
        p.id = patientSeq++;
        p.admissionDate = new Date();
        patientRecords.put(p.id, p);
        save();
        return p;
    }
    public synchronized void deletePatientRecord(Long id) { patientRecords.remove(id); save(); }
    public synchronized void deleteOrg(Long id) {
        orgs.remove(id);
        needs.values().removeIf(n -> Objects.equals(n.organizationId, id));
        facilities.values().removeIf(f -> Objects.equals(f.organizationId, id));
        save();
    }
    public synchronized void deleteNeed(Long id) {
        needs.remove(id);
        donations.values().removeIf(d -> Objects.equals(d.needId, id));
        save();
    }
 
    private static final File SAVE_FILE = new File("sahaara_connect.data");
 
    public static AppState loadOrNew(File file) {
        if (file.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
                AppState s = (AppState) ois.readObject();
                s.ensureSeed();
                return s;
            } catch (Exception e) {
                System.out.println("Load failed, starting fresh: " + e.getMessage());
            }
        }
        AppState s = new AppState();
        s.seed();
        s.save();
        return s;
    }
    public synchronized void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
            oos.writeObject(this);
        } catch (Exception e) {
            System.out.println("Save failed: " + e.getMessage());
        }
    }
    private void ensureSeed() { if (orgs.isEmpty()) seed(); }
 
    private void seed() {
        // --- ORPHANAGES (only those with working websites) ---
        Organization o1 = new Organization(); o1.name = "Prerana Charitable Trust"; o1.category = "Orphanage";
        o1.area = "Bangalore"; o1.address = "Indiranagar, Bangalore"; o1.email = "contact@prerana.org";
        o1.phone = "+91-9876543210"; o1.website = "https://prerana.org";
        o1.description = "Shelter, education, and care for orphaned children in Bengaluru.";
        o1.demoQRCode = "PRERANA_ORG_QR_2024"; o1.demoQRLabel = "Prerana Trust – Donation QR";
        addOrg(o1);
 
        Organization o2 = new Organization(); o2.name = "Snehalaya"; o2.category = "Orphanage";
        o2.area = "Bangalore"; o2.address = "Whitefield, Bangalore"; o2.email = "info@snehalaya.org";
        o2.phone = "+91-9880012345"; o2.website = "https://snehalaya.org";
        o2.description = "Home for abandoned and orphaned children with full residential care.";
        o2.demoQRCode = "SNEHALAYA_ORG_QR_2024"; o2.demoQRLabel = "Snehalaya – Donation QR";
        addOrg(o2);
 
        Organization o3 = new Organization(); o3.name = "Ashraya"; o3.category = "Orphanage";
        o3.area = "Bangalore"; o3.address = "Indiranagar, Bangalore"; o3.email = "contact@ashraya.org";
        o3.phone = "+91-80-25251234"; o3.website = "https://ashraya.org";
        o3.description = "Care and education for orphaned children, focused on holistic development.";
        o3.demoQRCode = "ASHRAYA_ORG_QR_2024"; o3.demoQRLabel = "Ashraya – Donation QR";
        addOrg(o3);
 
        Organization o4 = new Organization(); o4.name = "Shishu Mandir"; o4.category = "Orphanage";
        o4.area = "Bangalore"; o4.address = "Whitefield, Bangalore"; o4.email = "info@shishumandir.org";
        o4.phone = "+91-9880012346"; o4.website = "https://shishumandir.org";
        o4.description = "Education and shelter for underprivileged children in East Bangalore.";
        o4.demoQRCode = "SHISHUMANDIR_ORG_QR_2024"; o4.demoQRLabel = "Shishu Mandir – Donation QR";
        addOrg(o4);
 
        Organization o5 = new Organization(); o5.name = "Light House Children Foundation"; o5.category = "Orphanage";
        o5.area = "Bangalore"; o5.address = "Marathahalli, Bangalore"; o5.email = "contact@lighthousechildren.org";
        o5.phone = "+91-9876543212"; o5.website = "https://lighthousechildren.org";
        o5.description = "Holistic care, education and vocational programs for children.";
        o5.demoQRCode = "LIGHTHOUSE_ORG_QR_2024"; o5.demoQRLabel = "Light House Foundation – Donation QR";
        addOrg(o5);
 
        // --- ASHRAMS (only those with working websites + email shown, NOT upi) ---
        Organization o6 = new Organization(); o6.name = "Sri Ramakrishna Math"; o6.category = "Ashram";
        o6.area = "Bangalore"; o6.address = "Basavanagudi, Bangalore"; o6.email = "info@rkmathbangalore.org";
        o6.phone = "+91-80-26612290"; o6.website = "https://rkmathbangalore.org";
        o6.description = "Promoting spiritual education and cultural values rooted in Vedanta.";
        o6.demoQRCode = "RKMATH_ASHRAM_QR_2024"; o6.demoQRLabel = "Sri Ramakrishna Math – Donation QR";
        addOrg(o6);
 
        Organization o7 = new Organization(); o7.name = "ISKCON Bangalore"; o7.category = "Ashram";
        o7.area = "Bangalore"; o7.address = "Rajajinagar, Bangalore"; o7.email = "info@iskconbangalore.org";
        o7.phone = "+91-80-23471956"; o7.website = "https://iskconbangalore.org";
        o7.description = "Spiritual center promoting Krishna consciousness and community service.";
        o7.demoQRCode = "ISKCON_ASHRAM_QR_2024"; o7.demoQRLabel = "ISKCON Bangalore – Donation QR";
        addOrg(o7);
 
        Organization o8 = new Organization(); o8.name = "Chinmaya Mission Bangalore"; o8.category = "Ashram";
        o8.area = "Bangalore"; o8.address = "Malleshwaram, Bangalore"; o8.email = "info@chinmayamission.com";
        o8.phone = "+91-80-23340466"; o8.website = "https://chinmayamission.com";
        o8.description = "Spiritual education, Vedanta study and cultural programs for all ages.";
        o8.demoQRCode = "CHINMAYA_ASHRAM_QR_2024"; o8.demoQRLabel = "Chinmaya Mission – Donation QR";
        addOrg(o8);
 
        // --- NEEDS ---
        Need n1 = new Need(); n1.title = "School Supplies and Books"; n1.type = "Items";
        n1.details = "Education materials for 50 children"; n1.amountTarget = 15000.0; n1.amountRaised = 5000.0;
        n1.organizationId = o1.id; addNeed(n1);
 
        Need n2 = new Need(); n2.title = "Monthly Food & Nutrition"; n2.type = "Money";
        n2.details = "Monthly food expenses for all resident children"; n2.amountTarget = 50000.0; n2.amountRaised = 20000.0;
        n2.organizationId = o2.id; addNeed(n2);
 
        Need n3 = new Need(); n3.title = "Winter Clothing Drive"; n3.type = "Items";
        n3.details = "Warm clothes for 75 children this winter"; n3.amountTarget = 25000.0; n3.amountRaised = 10000.0;
        n3.organizationId = o4.id; addNeed(n3);
 
        Need n4 = new Need(); n4.title = "Computer Lab Setup"; n4.type = "Money";
        n4.details = "Digital literacy lab for children"; n4.amountTarget = 100000.0; n4.amountRaised = 35000.0;
        n4.organizationId = o3.id; addNeed(n4);
 
        Need n5 = new Need(); n5.title = "Sports Equipment"; n5.type = "Items";
        n5.details = "Sports gear for physical education"; n5.amountTarget = 20000.0; n5.amountRaised = 7000.0;
        n5.organizationId = o5.id; addNeed(n5);
 
        Need n6 = new Need(); n6.title = "Skill Training Program"; n6.type = "Money";
        n6.details = "Vocational training for teenagers"; n6.amountTarget = 40000.0; n6.amountRaised = 15000.0;
        n6.organizationId = o1.id; addNeed(n6);
 
        // Healthcare Needs – Facility-level
        Need n7 = new Need(); n7.title = "Emergency Medical Fund"; n7.type = "Money";
        n7.details = "Emergency fund for sudden medical needs across the ashram"; n7.priority = "High";
        n7.status = "Pending"; n7.healthCategory = "Medical"; n7.healthNeedType = "Facility";
        n7.amountTarget = 50000.0; n7.amountRaised = 15000.0; n7.organizationId = o6.id; addNeed(n7);
 
        Need n8 = new Need(); n8.title = "Mental Health Counseling Program"; n8.type = "Services";
        n8.details = "Counseling sessions for emotionally vulnerable residents"; n8.priority = "Medium";
        n8.status = "In Progress"; n8.healthCategory = "Counseling"; n8.healthNeedType = "Facility";
        n8.amountTarget = 25000.0; n8.amountRaised = 8000.0; n8.organizationId = o8.id; addNeed(n8);
 
        Need n9 = new Need(); n9.title = "Annual Health Camp"; n9.type = "Services";
        n9.details = "Complete health checkup camp for all orphanage children"; n9.priority = "Medium";
        n9.status = "Pending"; n9.healthCategory = "Medical"; n9.healthNeedType = "Facility";
        n9.amountTarget = 30000.0; n9.amountRaised = 5000.0; n9.organizationId = o2.id; addNeed(n9);
 
        // Healthcare Needs – Individual Patient
        Need n10 = new Need(); n10.title = "Medicines for Arjun – Asthma"; n10.type = "Money";
        n10.details = "Monthly medicines for a child with chronic asthma"; n10.priority = "High";
        n10.status = "In Progress"; n10.healthCategory = "Medicines"; n10.healthNeedType = "Patient";
        n10.beneficiaryName = "Arjun Kumar"; n10.beneficiaryAge = 12; n10.medicalCondition = "Asthma";
        n10.amountTarget = 30000.0; n10.amountRaised = 10000.0; n10.organizationId = o2.id; addNeed(n10);
 
        Need n11 = new Need(); n11.title = "Surgery Fund – Priya"; n11.type = "Money";
        n11.details = "Corrective surgery for a young girl with bone deformity"; n11.priority = "Urgent";
        n11.status = "Pending"; n11.healthCategory = "Surgery"; n11.healthNeedType = "Patient";
        n11.beneficiaryName = "Priya Sharma"; n11.beneficiaryAge = 9; n11.medicalCondition = "Bone Deformity";
        n11.amountTarget = 80000.0; n11.amountRaised = 20000.0; n11.organizationId = o3.id; addNeed(n11);
 
        // Healthcare Facilities
        HealthcareFacility h1 = new HealthcareFacility(); h1.name = "Prerana Medical Clinic"; h1.type = "Clinic";
        h1.area = "Indiranagar"; h1.address = "Indiranagar, Bangalore"; h1.phone = "+91-080-25251100";
        h1.email = "clinic@prerana.org"; h1.services = "General, Pediatrics, Immunization";
        h1.capacity = 20; h1.availability = "9AM-5PM Weekdays"; h1.qualifications = "MBBS, MD";
        h1.operationalCost = 50000.0; h1.organizationId = o1.id; addFacility(h1);
 
        HealthcareFacility h2 = new HealthcareFacility(); h2.name = "Snehalaya Health Center"; h2.type = "Clinic";
        h2.area = "Whitefield"; h2.address = "Whitefield, Bangalore"; h2.phone = "+91-080-29878876";
        h2.email = "health@snehalaya.org"; h2.services = "General, Mental Health, Dental";
        h2.capacity = 30; h2.availability = "24/7"; h2.qualifications = "MD, DDS";
        h2.equipment = "ECG, Blood Analyzer, X-Ray"; h2.operationalCost = 75000.0; h2.organizationId = o2.id; addFacility(h2);
 
        HealthcareFacility h3 = new HealthcareFacility(); h3.name = "Sri Ramakrishna Medical Services"; h3.type = "Hospital";
        h3.area = "Basavanagudi"; h3.address = "Basavanagudi, Bangalore"; h3.phone = "+91-080-26612290";
        h3.email = "medical@rkmathbangalore.org"; h3.services = "General, Surgery, Emergency, ICU";
        h3.capacity = 50; h3.availability = "24/7"; h3.qualifications = "MD, MS, DNB";
        h3.equipment = "Ventilators, Monitors, Ultrasound, CT Scan"; h3.operationalCost = 150000.0; h3.organizationId = o6.id; addFacility(h3);
 
        HealthcareFacility h4 = new HealthcareFacility(); h4.name = "ISKCON Wellness Centre"; h4.type = "Clinic";
        h4.area = "Rajajinagar"; h4.address = "Rajajinagar, Bangalore"; h4.phone = "+91-80-23471956";
        h4.email = "wellness@iskconbangalore.org"; h4.services = "Ayurveda, Yoga Therapy, Counseling, Nutrition";
        h4.capacity = 25; h4.availability = "8AM-6PM Daily"; h4.qualifications = "BAMS, MBBS";
        h4.equipment = "Panchakarma setup, Pulse oximeter, BP monitor"; h4.operationalCost = 60000.0; h4.organizationId = o7.id; addFacility(h4);
 
        HealthcareFacility h5 = new HealthcareFacility(); h5.name = "Chinmaya Community Health Hub"; h5.type = "Clinic";
        h5.area = "Malleshwaram"; h5.address = "Malleshwaram, Bangalore"; h5.phone = "+91-80-23340466";
        h5.email = "health@chinmayamission.com"; h5.services = "General Medicine, Geriatrics, Mental Health, Physiotherapy";
        h5.capacity = 35; h5.availability = "9AM-7PM Weekdays, 9AM-2PM Saturday"; h5.qualifications = "MBBS, MD, MPT";
        h5.equipment = "ECG, Spirometer, X-Ray, Blood Analyzer"; h5.operationalCost = 80000.0; h5.organizationId = o8.id; addFacility(h5);
 
        HealthcareFacility h6 = new HealthcareFacility(); h6.name = "Light House Children's Clinic"; h6.type = "Clinic";
        h6.area = "Marathahalli"; h6.address = "Marathahalli, Bangalore"; h6.phone = "+91-9876543212";
        h6.email = "health@lighthousechildren.org"; h6.services = "Pediatrics, Immunization, Dental, Eye Care";
        h6.capacity = 20; h6.availability = "9AM-5PM Weekdays"; h6.qualifications = "MBBS, DCH";
        h6.equipment = "Vaccine cold chain, Vision tester, Dental chair"; h6.operationalCost = 45000.0; h6.organizationId = o5.id; addFacility(h6);
 
        // Patient records
        PatientRecord p1 = new PatientRecord(); p1.patientName = "Arjun Kumar"; p1.age = 12; p1.gender = "Male";
        p1.facilityId = h2.id; p1.organizationId = o2.id; p1.medicalCondition = "Asthma";
        p1.treatmentPlan = "Inhalers and breathing exercises"; p1.status = "Active";
        p1.treatmentCost = 5000.0; p1.paidAmount = 2500.0; p1.notes = "Regular follow-up required"; addPatientRecord(p1);
 
        PatientRecord p2 = new PatientRecord(); p2.patientName = "Priya Sharma"; p2.age = 9; p2.gender = "Female";
        p2.facilityId = h1.id; p2.organizationId = o3.id; p2.medicalCondition = "Bone Deformity";
        p2.treatmentPlan = "Corrective surgery and physiotherapy"; p2.status = "Pending Surgery";
        p2.treatmentCost = 80000.0; p2.paidAmount = 20000.0; p2.notes = "Surgery scheduled for next month"; addPatientRecord(p2);
 
        PatientRecord p3 = new PatientRecord(); p3.patientName = "Rahul Desai"; p3.age = 7; p3.gender = "Male";
        p3.facilityId = h2.id; p3.organizationId = o2.id; p3.medicalCondition = "Malnutrition";
        p3.treatmentPlan = "Dietary supplements, high-protein diet, weekly monitoring"; p3.status = "Recovering";
        p3.treatmentCost = 12000.0; p3.paidAmount = 8000.0; p3.notes = "Improving steadily; weight gain observed"; addPatientRecord(p3);
 
        PatientRecord p4 = new PatientRecord(); p4.patientName = "Meera Nair"; p4.age = 14; p4.gender = "Female";
        p4.facilityId = h3.id; p4.organizationId = o6.id; p4.medicalCondition = "Typhoid";
        p4.treatmentPlan = "Antibiotics course, IV fluids, bed rest"; p4.status = "Active";
        p4.treatmentCost = 18000.0; p4.paidAmount = 18000.0; p4.notes = "Expected discharge in 5 days"; addPatientRecord(p4);
 
        PatientRecord p5 = new PatientRecord(); p5.patientName = "Suresh Babu"; p5.age = 11; p5.gender = "Male";
        p5.facilityId = h4.id; p5.organizationId = o7.id; p5.medicalCondition = "Anxiety & Depression";
        p5.treatmentPlan = "Weekly counseling sessions, breathing exercises, art therapy"; p5.status = "Active";
        p5.treatmentCost = 9000.0; p5.paidAmount = 4500.0; p5.notes = "Showing positive response to counseling"; addPatientRecord(p5);
 
        PatientRecord p6 = new PatientRecord(); p6.patientName = "Kavya Reddy"; p6.age = 6; p6.gender = "Female";
        p6.facilityId = h6.id; p6.organizationId = o5.id; p6.medicalCondition = "Dental Caries (severe)";
        p6.treatmentPlan = "Root canal treatment, dental restoration, fluoride application"; p6.status = "Recovering";
        p6.treatmentCost = 6500.0; p6.paidAmount = 6500.0; p6.notes = "Treatment completed; follow-up in 3 months"; addPatientRecord(p6);
 
        PatientRecord p7 = new PatientRecord(); p7.patientName = "Vijay Kumar"; p7.age = 16; p7.gender = "Male";
        p7.facilityId = h5.id; p7.organizationId = o8.id; p7.medicalCondition = "Tuberculosis (Pulmonary)";
        p7.treatmentPlan = "DOTS therapy, 6-month anti-TB medication regimen"; p7.status = "Active";
        p7.treatmentCost = 22000.0; p7.paidAmount = 10000.0; p7.notes = "On month 2 of DOTS; sputum follow-up due"; addPatientRecord(p7);
    }
}
 
class Organization implements Serializable {
    public Long id; public String name; public String category; public String area;
    public String address; public String email; public String phone; public String website; public String description;
    public String demoQRCode;  // Internal QR code identifier (not UPI, not payment link – just demo ref)
    public String demoQRLabel;
}
class Need implements Serializable {
    public Long id; public String title; public String type;
    public Double amountTarget; public Double amountRaised;
    public String details; public Long organizationId;
    public String priority;
    public String status;
    public String healthCategory;
    public String healthNeedType; // "Facility" or "Patient"
    public String beneficiaryName;
    public Integer beneficiaryAge;
    public String medicalCondition;
    public Date createdAt;
    public Date dueDate;
}
class Donation implements Serializable {
    public Long id; public Long needId; public Double amount; public String note;
    public String donorName;
    public Date pledgedAt = new Date(); public boolean fulfilled = false;
    public String organizationName;
    public Date donationDate;
    public String paymentMethod; // "QR", "Card", "UPI"
    public String receiptNumber;
 
    public static Double calculateTaxBenefit(Double donationAmount) {
        if (donationAmount == null || donationAmount <= 0) return 0.0;
        return (donationAmount * 0.5 * 0.30);
    }
}
class HealthcareFacility implements Serializable {
    public Long id; public String name; public String type; public String area; public String address;
    public String phone; public String email; public Long organizationId;
    public String services; public Integer capacity; public String availability;
    public String qualifications; public String equipment; public Double operationalCost;
    public Date lastUpdated;
}
class PatientRecord implements Serializable {
    public Long id; public Long facilityId; public Long organizationId;
    public String patientName; public Integer age; public String gender;
    public String medicalCondition; public String treatmentPlan;
    public Date admissionDate; public Date dischargeDate;
    public String status;
    public Double treatmentCost; public Double paidAmount;
    public String notes; public Date lastVisit;
}
 
// ===== Router & pages =====
class Router {
    private final AppState s;
    public Router(AppState s) { this.s = s; }
 
    // HOME PAGE
    public void home(HttpExchange ex) throws IOException {
        String featuredOrgs = s.orgs.values().stream().limit(6)
                .map(o -> "<li><strong>" + esc(o.name) + "</strong> <span class='tag'>" + esc(nz(o.category)) + "</span></li>")
                .collect(Collectors.joining());
 
        String activeNeeds = s.needs.values().stream().limit(5)
                .map(n -> {
                    Organization org = s.orgs.get(n.organizationId);
                    String orgName = org != null ? org.name : "";
                    return "<li>" + esc(n.title) + " <small>— " + esc(orgName) + "</small></li>";
                })
                .collect(Collectors.joining());
 
        double totalDonated = s.donations.values().stream().mapToDouble(d -> d.amount == null ? 0.0 : d.amount).sum();
        int donationCount = s.donations.size();
 
        // Top donors – from completed donations
        Map<String, Double> donorTotals = new LinkedHashMap<>();
        for (Donation d : s.donations.values()) {
            if (d.amount != null && d.amount > 0) {
                String name = d.donorName != null && !d.donorName.isBlank() ? d.donorName : "Anonymous";
                donorTotals.merge(name, d.amount, Double::sum);
            }
        }
        String topDonorsHtml = donorTotals.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> "<li><span class='donor-name'>" + esc(e.getKey()) + "</span>"
                        + "<span class='donor-amt'>₹" + String.format("%.0f", e.getValue()) + "</span></li>")
                .collect(Collectors.joining());
 
        String topDonorsSection = topDonorsHtml.isBlank() ? "" :
            "<div class='card'><h3>🏆 Top Donors</h3><ul class='donor-list'>" + topDonorsHtml + "</ul></div>";
 
        String inner = ""
            + "<div id='splash' class='splash'><div class='splash-inner'>"
            + "<div class='splash-emblem'>🤝</div>"
            + "<div class='splash-text'>Sahaara Connect</div>"
            + "<div class='splash-sub'>Bengaluru's Bridge of Compassion</div>"
            + "</div></div>"
            + "<section class='hero'>"
            + "  <h1 class='title'>Bridge Compassion<br>with Action</h1>"
            + "  <p class='subtitle'>Connect orphanages and ashrams across Bengaluru with donors, volunteers, and healthcare support.</p>"
            + "  <div class='cta'>"
            + "    <a href='/organizations' class='btn primary'>🏛 Explore Organizations</a>"
            + "    <a href='/donate' class='btn'>💝 Donate Now</a>"
            + "    <a href='/needs' class='btn'>📋 View Needs</a>"
            + "  </div>"
            + "</section>"
            + "<blockquote class='quote'>\"The smallest act of kindness is worth more than the grandest intention.\" — Oscar Wilde</blockquote>"
            + "<section class='stats-row'>"
            + "  <div class='stat-box'><div class='stat-num'>₹" + String.format("%.0f", totalDonated) + "</div><div class='stat-label'>Total Donated</div></div>"
            + "  <div class='stat-box'><div class='stat-num'>" + donationCount + "</div><div class='stat-label'>Donations Made</div></div>"
            + "  <div class='stat-box'><div class='stat-num'>" + s.orgs.size() + "</div><div class='stat-label'>Organizations</div></div>"
            + "  <div class='stat-box'><div class='stat-num'>" + s.needs.size() + "</div><div class='stat-label'>Active Needs</div></div>"
            + "</section>"
            + "<section class='grid'>"
            + "  <div class='card'><h3>🏛 Featured Organizations</h3><ul class='feature-list'>" + featuredOrgs + "</ul>"
            + "  <a href='/organizations' class='btn' style='margin-top:16px;display:inline-block'>View All</a></div>"
            + "  <div class='card'><h3>📋 Active Needs</h3><ul class='feature-list'>" + activeNeeds + "</ul>"
            + "  <a href='/needs' class='btn' style='margin-top:16px;display:inline-block'>See All Needs</a></div>"
            + topDonorsSection
            + "</section>";
        Html.ok(ex, Html.layout("Home", inner));
    }
 
    // ORGANIZATIONS PAGE
    public void organizations(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex);
        String area = q.getOrDefault("area", "");
        String search = q.getOrDefault("q", "");
        String category = q.getOrDefault("category", "");
 
        List<Organization> list = s.orgs.values().stream()
                .filter(o -> area.isBlank() || (o.area!=null && o.area.equalsIgnoreCase(area)))
                .filter(o -> search.isBlank() || (o.name!=null && o.name.toLowerCase().contains(search.toLowerCase())))
                .filter(o -> category.isBlank() || (o.category!=null && o.category.equalsIgnoreCase(category)))
                .sorted(Comparator.comparing(o -> o.name == null ? "" : o.name))
                .collect(Collectors.toList());
 
        String cards = list.stream().map(o -> ""
                + "<div class='card org-card'>"
                + "  <div class='org-cat-badge " + (o.category != null && o.category.equals("Ashram") ? "ashram" : "orphanage") + "'>" + esc(nz(o.category)) + "</div>"
                + "  <h3>" + esc(nz(o.name)) + "</h3>"
                + "  <p>" + esc(nz(o.description)) + "</p>"
                + "  <p><span class='meta-label'>📍</span> " + esc(nz(o.address)) + "</p>"
                + "  <p><span class='meta-label'>✉️</span> <a href='mailto:" + esc(nz(o.email)) + "'>" + esc(nz(o.email)) + "</a></p>"
                + "  <p><span class='meta-label'>📞</span> " + esc(nz(o.phone)) + "</p>"
                + (nz(o.website).isBlank() ? "" : "  <p><a href='" + esc(o.website) + "' target='_blank' class='website-link'>🌐 Visit Website</a></p>")
                + "  <div style='margin-top:16px;display:flex;gap:8px;flex-wrap:wrap'>"
                + "    <a href='/donate?orgId=" + o.id + "' class='btn primary'>💝 Donate Now</a>"
                + "    <a href='/needs?orgId=" + o.id + "' class='btn'>📋 View Needs</a>"
                + "  </div>"
                + "</div>"
        ).collect(Collectors.joining());
 
        String inner = ""
            + "<h2 class='page-title'>🏛 Organizations</h2>"
            + "<section class='toolbar'>"
            + "  <form method='get' class='inline'>"
            + "    <input type='text' name='q' placeholder='Search by name...' value='" + esc(search) + "'>"
            + "    <select name='category'>"
            + "      <option value=''>All Categories</option>"
            + "      <option value='Orphanage'" + ("Orphanage".equals(category) ? " selected" : "") + ">🏠 Orphanages</option>"
            + "      <option value='Ashram'" + ("Ashram".equals(category) ? " selected" : "") + ">🕉 Ashrams</option>"
            + "    </select>"
            + "    <button class='btn primary'>Search</button>"
            + "  </form>"
            + "</section>"
            + "<section class='grid'>" + (cards.isBlank() ? "<p>No organizations found.</p>" : cards) + "</section>";
        Html.ok(ex, Html.layout("Organizations", inner));
    }
 
    public void orgCreate(HttpExchange ex) throws IOException {
        Map<String,String> f = readForm(ex);
        Organization o = new Organization();
        o.name = f.get("name"); o.category = f.get("category"); o.area = f.get("area");
        o.address = f.get("address"); o.email = f.get("email"); o.phone = f.get("phone");
        o.website = f.get("website"); o.description = f.get("description");
        if (nz(o.name).isBlank() || nz(o.category).isBlank() || nz(o.area).isBlank()) {
            Html.redirect(ex, "/organizations"); return;
        }
        s.addOrg(o);
        Html.redirect(ex, "/organizations");
    }
 
    public void orgDelete(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex);
        String idStr = q.get("id");
        if (idStr != null) { try { s.deleteOrg(Long.parseLong(idStr)); } catch (Exception ignored) {} }
        Html.redirect(ex, "/organizations");
    }
 
    // NEEDS PAGE
    private String formatNeedCard(Need n) {
        Organization o = s.orgs.get(n.organizationId);
        String orgName = o != null ? o.name : "Unknown";
        String priorColor = "Urgent".equals(n.priority) ? "#ef4444" : "High".equals(n.priority) ? "#f59e0b" : "#10b981";
        double pct = (n.amountTarget != null && n.amountTarget > 0 && n.amountRaised != null) ?
                Math.min(100, (n.amountRaised / n.amountTarget) * 100) : 0;
        return ""
            + "<div class='card need-card' style='border-left:4px solid " + priorColor + "'>"
            + "  <h3>" + esc(nz(n.title)) + "</h3>"
            + "  <p style='color:#64748b'>" + esc(nz(n.details)) + "</p>"
            + (n.beneficiaryName != null && !n.beneficiaryName.isBlank() ?
                "<p><strong>Beneficiary:</strong> " + esc(n.beneficiaryName) + (n.beneficiaryAge != null ? ", Age " + n.beneficiaryAge : "") + "</p>" : "")
            + (n.medicalCondition != null && !n.medicalCondition.isBlank() ?
                "<p><strong>Condition:</strong> " + esc(n.medicalCondition) + "</p>" : "")
            + "<p>"
            + "<span class='tag'>" + esc(nz(n.type)) + "</span> "
            + (n.healthCategory != null && !n.healthCategory.isBlank() ? "<span class='tag health'>" + esc(n.healthCategory) + "</span> " : "")
            + (n.priority != null ? "<span class='tag' style='background:" + priorColor + ";color:#fff'>" + esc(n.priority) + "</span>" : "")
            + "</p>"
            + "<p style='font-size:13px;color:#475569'><strong>Organization:</strong> " + esc(orgName) + "</p>"
            + (n.amountTarget != null ? ""
                + "<div class='progress-wrap'>"
                + "  <div style='display:flex;justify-content:space-between;font-size:12px;margin-bottom:4px'>"
                + "    <span>Raised: ₹" + String.format("%.0f", n.amountRaised != null ? n.amountRaised : 0) + "</span>"
                + "    <span>Target: ₹" + String.format("%.0f", n.amountTarget) + "</span>"
                + "  </div>"
                + "  <div class='progress-bar'><div class='progress-fill' style='width:" + String.format("%.1f", pct) + "%'></div></div>"
                + "</div>" : "")
            + "  <a class='btn primary' href='/donate?needId=" + n.id + "&orgId=" + (n.organizationId != null ? n.organizationId : "") + "' style='margin-top:12px'>💝 Donate for This</a>"
            + "</div>";
    }
 
    public void needs(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex);
        String type = q.getOrDefault("type", "");
        String priority = q.getOrDefault("priority", "");
        String healthCategory = q.getOrDefault("healthCategory", "");
        String orgIdStr = q.getOrDefault("orgId", "");
 
        List<Need> list = s.needs.values().stream()
                .filter(n -> type.isBlank() || (n.type!=null && n.type.equalsIgnoreCase(type)))
                .filter(n -> priority.isBlank() || (n.priority!=null && n.priority.equalsIgnoreCase(priority)))
                .filter(n -> healthCategory.isBlank() || (n.healthCategory!=null && n.healthCategory.equalsIgnoreCase(healthCategory)))
                .filter(n -> orgIdStr.isBlank() || String.valueOf(n.organizationId).equals(orgIdStr))
                .sorted(Comparator.comparing((Need n) -> {
                    if ("Urgent".equals(n.priority)) return 0;
                    if ("High".equals(n.priority)) return 1;
                    if ("Medium".equals(n.priority)) return 2;
                    return 3;
                }).thenComparing(n -> n.title == null ? "" : n.title))
                .collect(Collectors.toList());
 
        // Split into healthcare facility, patient, and general needs
        List<Need> facilityHealthNeeds = list.stream()
                .filter(n -> "Facility".equals(n.healthNeedType))
                .collect(Collectors.toList());
        List<Need> patientHealthNeeds = list.stream()
                .filter(n -> "Patient".equals(n.healthNeedType))
                .collect(Collectors.toList());
        List<Need> generalNeeds = list.stream()
                .filter(n -> n.healthNeedType == null || (!n.healthNeedType.equals("Facility") && !n.healthNeedType.equals("Patient")))
                .collect(Collectors.toList());
 
        String facilitySection = facilityHealthNeeds.isEmpty() ? "" :
            "<div class='health-section facility-section'>"
            + "<div class='health-section-header'><span class='health-icon'>🏥</span><div>"
            + "<h3>Complete Ashram / Orphanage Healthcare Needs</h3>"
            + "<p>Facility-wide medical programs, equipment, and infrastructure for the whole community</p>"
            + "</div></div>"
            + "<div class='grid'>" + facilityHealthNeeds.stream().map(this::formatNeedCard).collect(Collectors.joining()) + "</div>"
            + "</div>";
 
        String patientSection = patientHealthNeeds.isEmpty() ? "" :
            "<div class='health-section patient-section'>"
            + "<div class='health-section-header'><span class='health-icon'>🧑‍⚕️</span><div>"
            + "<h3>Individual Patient Care Needs</h3>"
            + "<p>Specific treatment costs and medical needs for individual patients</p>"
            + "</div></div>"
            + "<div class='grid'>" + patientHealthNeeds.stream().map(this::formatNeedCard).collect(Collectors.joining()) + "</div>"
            + "</div>";
 
        String generalSection = generalNeeds.isEmpty() ? "" :
            "<div class='health-section general-section'>"
            + "<div class='health-section-header'><span class='health-icon'>📦</span><div>"
            + "<h3>General Needs</h3>"
            + "<p>Education, food, clothing, and other non-medical needs</p>"
            + "</div></div>"
            + "<div class='grid'>" + generalNeeds.stream().map(this::formatNeedCard).collect(Collectors.joining()) + "</div>"
            + "</div>";
 
        String orgOptions = s.orgs.values().stream()
                .sorted(Comparator.comparing(o -> o.name == null ? "" : o.name))
                .map(o -> "<option value='" + o.id + "'>" + esc(nz(o.name)) + " (" + esc(nz(o.category)) + ")</option>")
                .collect(Collectors.joining());
 
        String inner = ""
            + "<h2 class='page-title'>📋 Needs & Requests</h2>"
            + "<section class='toolbar'>"
            + "  <form method='get' class='inline'>"
            + "    <select name='type'><option value=''>All Types</option>"
            + "      <option value='Money'" + ("Money".equals(type) ? " selected" : "") + ">💰 Money</option>"
            + "      <option value='Items'" + ("Items".equals(type) ? " selected" : "") + ">📦 Items</option>"
            + "      <option value='Services'" + ("Services".equals(type) ? " selected" : "") + ">🤝 Services</option>"
            + "    </select>"
            + "    <select name='priority'><option value=''>All Priorities</option>"
            + "      <option value='Urgent'" + ("Urgent".equals(priority) ? " selected" : "") + ">🔴 Urgent</option>"
            + "      <option value='High'" + ("High".equals(priority) ? " selected" : "") + ">🟠 High</option>"
            + "      <option value='Medium'" + ("Medium".equals(priority) ? " selected" : "") + ">🟡 Medium</option>"
            + "    </select>"
            + "    <select name='healthCategory'><option value=''>All Health Categories</option>"
            + "      <option value='Medical'" + ("Medical".equals(healthCategory) ? " selected" : "") + ">Medical</option>"
            + "      <option value='Medicines'" + ("Medicines".equals(healthCategory) ? " selected" : "") + ">Medicines</option>"
            + "      <option value='Surgery'" + ("Surgery".equals(healthCategory) ? " selected" : "") + ">Surgery</option>"
            + "      <option value='Counseling'" + ("Counseling".equals(healthCategory) ? " selected" : "") + ">Counseling</option>"
            + "    </select>"
            + "    <button class='btn primary'>Filter</button>"
            + "  </form>"
            + "</section>"
            + facilitySection + patientSection + generalSection;
        Html.ok(ex, Html.layout("Needs", inner));
    }
 
    public void needCreate(HttpExchange ex) throws IOException {
        Map<String,String> f = readForm(ex);
        Need n = new Need();
        n.title = f.get("title"); n.type = f.get("type"); n.details = f.get("details");
        n.priority = f.getOrDefault("priority", "Medium"); n.status = f.getOrDefault("status", "Pending");
        n.healthCategory = f.get("healthCategory"); n.healthNeedType = f.get("healthNeedType");
        n.beneficiaryName = f.get("beneficiaryName"); n.beneficiaryAge = parseInteger(f.get("beneficiaryAge"));
        n.medicalCondition = f.get("medicalCondition");
        n.amountTarget = parseDouble(f.get("amountTarget")); n.amountRaised = 0.0;
        n.organizationId = parseLong(f.get("orgId")); n.createdAt = new Date();
        if (nz(n.title).isBlank() || nz(n.type).isBlank() || n.organizationId == null) {
            Html.redirect(ex, "/needs"); return;
        }
        s.addNeed(n);
        Html.redirect(ex, "/needs");
    }
 
    public void needDelete(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex);
        String idStr = q.get("id");
        if (idStr != null) { try { s.deleteNeed(Long.parseLong(idStr)); } catch (Exception ignored) {} }
        Html.redirect(ex, "/needs");
    }
 
    // DONATE PAGE – unified payment with QR / Card / UPI selection
    public void donate(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex);
        Long preSelectedOrgId = parseLong(q.get("orgId"));
        Long preSelectedNeedId = parseLong(q.get("needId"));
 
        // Build Orphanage dropdown options
        String orphanageOptions = s.orgs.values().stream()
                .filter(o -> "Orphanage".equals(o.category))
                .sorted(Comparator.comparing(o -> o.name == null ? "" : o.name))
                .map(o -> "<option value='" + o.id + "'" + (preSelectedOrgId != null && preSelectedOrgId.equals(o.id) ? " selected" : "") + ">"
                        + esc(nz(o.name)) + "</option>")
                .collect(Collectors.joining());
 
        // Build Ashram dropdown options
        String ashramOptions = s.orgs.values().stream()
                .filter(o -> "Ashram".equals(o.category))
                .sorted(Comparator.comparing(o -> o.name == null ? "" : o.name))
                .map(o -> "<option value='" + o.id + "'" + (preSelectedOrgId != null && preSelectedOrgId.equals(o.id) ? " selected" : "") + ">"
                        + esc(nz(o.name)) + "</option>")
                .collect(Collectors.joining());
 
        // Build all organizations for combined dropdown
        String allOrgOptions = "<option value=''>-- Choose Organization --</option>"
                + (orphanageOptions.isBlank() ? "" : "<optgroup label='🏠 Orphanages'>" + orphanageOptions + "</optgroup>")
                + (ashramOptions.isBlank() ? "" : "<optgroup label='🕉 Ashrams'>" + ashramOptions + "</optgroup>");
 
        // Build needs dropdown pre-selection if needId given
        String needsOptions = "<option value=''>-- General Donation (No specific need) --</option>"
                + s.needs.values().stream()
                    .sorted(Comparator.comparing(n -> n.title == null ? "" : n.title))
                    .map(n -> {
                        Organization org = s.orgs.get(n.organizationId);
                        String orgName = org != null ? " [" + org.name + "]" : "";
                        return "<option value='" + n.id + "'" + (preSelectedNeedId != null && preSelectedNeedId.equals(n.id) ? " selected" : "") + ">"
                                + esc(nz(n.title)) + esc(orgName) + "</option>";
                    })
                    .collect(Collectors.joining());
 
        // Build demo QR list from organizations
        String qrListHtml = s.orgs.values().stream()
                .filter(o -> o.demoQRCode != null && !o.demoQRCode.isBlank())
                .sorted(Comparator.comparing(o -> o.name == null ? "" : o.name))
                .map(o -> {
                    String selectedAttr = (preSelectedOrgId != null && preSelectedOrgId.equals(o.id)) ? " selected" : "";
                    return "<option value='" + o.id + "'" + selectedAttr + ">" + esc(o.demoQRLabel != null ? o.demoQRLabel : o.name) + "</option>";
                })
                .collect(Collectors.joining());
 
        String inner = ""
            + "<h2 class='page-title'>💝 Make a Donation</h2>"
            + "<p style='color:#475569;text-align:center;margin-bottom:32px'>Your generosity helps orphanages and ashrams across Bengaluru.</p>"
            + "<div class='donate-container'>"
            // Step 1: Select Organization & Need
            + "<div class='card donate-step'>"
            + "  <div class='step-num'>1</div>"
            + "  <h3>Select Organization &amp; Purpose</h3>"
            + "  <div id='org-need-form'>"
            + "    <label>Choose an Organization</label>"
            + "    <select id='orgSelect' name='orgId' onchange='updateOrgQR(this)'>"
            + "    " + allOrgOptions
            + "    </select>"
            + "    <label>Specific Need <span style='font-weight:400;color:#94a3b8'>(optional)</span></label>"
            + "    <select id='needSelect' name='needId'>" + needsOptions + "</select>"
            + "    <label>Donation Amount (₹) *</label>"
            + "    <div class='amount-shortcuts'>"
            + "      <button type='button' class='amt-btn' onclick='setAmt(500)'>₹500</button>"
            + "      <button type='button' class='amt-btn' onclick='setAmt(1000)'>₹1,000</button>"
            + "      <button type='button' class='amt-btn' onclick='setAmt(2500)'>₹2,500</button>"
            + "      <button type='button' class='amt-btn' onclick='setAmt(5000)'>₹5,000</button>"
            + "      <button type='button' class='amt-btn' onclick='setAmt(10000)'>₹10,000</button>"
            + "    </div>"
            + "    <input type='number' id='amtInput' name='amount' min='10' step='10' placeholder='Enter amount' required>"
            + "  </div>"
            + "</div>"
            // Step 2: Payment Method
            + "<div class='card donate-step'>"
            + "  <div class='step-num'>2</div>"
            + "  <h3>Choose Payment Method</h3>"
            + "  <div class='payment-method-tabs'>"
            + "    <button type='button' class='pay-tab active' onclick='selectPayMethod(\"qr\",this)'>📱 QR Code</button>"
            + "    <button type='button' class='pay-tab' onclick='selectPayMethod(\"upi\",this)'>💳 UPI</button>"
            + "    <button type='button' class='pay-tab' onclick='selectPayMethod(\"card\",this)'>🏦 Card</button>"
            + "  </div>"
            // QR Panel
            + "  <div id='panel-qr' class='pay-panel'>"
            + "    <p style='color:#475569;font-size:14px'>Select a verified QR code from the organization. Scan the displayed QR with your payment app.</p>"
            + "    <label>Select Organization QR</label>"
            + "    <select id='qrSelect' name='qrOrgId' onchange='showQRPreview(this)'>"
            + "      <option value=''>-- Choose Organization QR --</option>" + qrListHtml
            + "    </select>"
            + "    <div id='qr-preview' style='display:none;margin-top:16px;text-align:center'>"
            + "      <div id='qr-box' class='qr-demo-box'></div>"
            + "      <p id='qr-label' style='font-size:13px;color:#475569;margin-top:8px'></p>"
            + "    </div>"
            + "  </div>"
            // UPI Panel
            + "  <div id='panel-upi' class='pay-panel' style='display:none'>"
            + "    <p style='color:#475569;font-size:14px'>Enter your UPI ID to pay directly.</p>"
            + "    <label>Your UPI ID *</label>"
            + "    <input type='text' id='upiInput' name='upiId' placeholder='yourname@upi or mobile@bankcode'>"
            + "    <p style='font-size:12px;color:#10b981' id='upi-valid-msg'></p>"
            + "  </div>"
            // Card Panel
            + "  <div id='panel-card' class='pay-panel' style='display:none'>"
            + "    <p style='color:#475569;font-size:14px'>Enter your card details for secure payment.</p>"
            + "    <label>Card Number</label>"
            + "    <input type='text' id='cardNum' name='cardNumber' placeholder='•••• •••• •••• ••••' maxlength='19' oninput='formatCard(this)'>"
            + "    <div class='form-row' style='margin:0'>"
            + "      <div><label>Expiry (MM/YY)</label><input type='text' name='cardExpiry' placeholder='MM/YY' maxlength='5'></div>"
            + "      <div><label>CVV</label><input type='password' name='cardCvv' placeholder='•••' maxlength='4'></div>"
            + "    </div>"
            + "    <label>Name on Card</label>"
            + "    <input type='text' name='cardName' placeholder='As on card'>"
            + "  </div>"
            + "</div>"
            // Step 3: Donor Info + Submit
            + "<div class='card donate-step'>"
            + "  <div class='step-num'>3</div>"
            + "  <h3>Your Details &amp; Confirm</h3>"
            + "  <form method='post' action='/donate/checkout' id='donateForm'>"
            + "    <input type='hidden' name='payMethod' id='payMethodHidden' value='qr'>"
            + "    <input type='hidden' name='orgId' id='orgIdHidden'>"
            + "    <input type='hidden' name='needId' id='needIdHidden'>"
            + "    <input type='hidden' name='amount' id='amountHidden'>"
            + "    <input type='hidden' name='qrOrgId' id='qrOrgIdHidden'>"
            + "    <input type='hidden' name='upiId' id='upiIdHidden'>"
            + "    <label>Full Name *</label>"
            + "    <input name='donorName' id='donorNameInput' type='text' required placeholder='Your full name'>"
            + "    <label>Message <span style='font-weight:400;color:#94a3b8'>(optional)</span></label>"
            + "    <textarea name='message' placeholder='Any message for the organization...' rows='2'></textarea>"
            + "    <button class='btn primary' type='button' onclick='submitDonate()' style='width:100%;margin-top:8px;padding:16px;font-size:16px'>✅ Confirm &amp; Donate</button>"
            + "  </form>"
            + "</div>"
            + "</div>"
            + "<script>"
            + "var currentPayMethod='qr';"
            + "function selectPayMethod(m,btn){"
            + "  currentPayMethod=m;"
            + "  document.getElementById('payMethodHidden').value=m;"
            + "  document.querySelectorAll('.pay-tab').forEach(function(b){b.classList.remove('active');});"
            + "  btn.classList.add('active');"
            + "  document.querySelectorAll('.pay-panel').forEach(function(p){p.style.display='none';});"
            + "  document.getElementById('panel-'+m).style.display='block';"
            + "}"
            + "function setAmt(v){"
            + "  document.getElementById('amtInput').value=v;"
            + "  document.getElementById('amountHidden').value=v;"
            + "}"
            + "document.getElementById('amtInput').addEventListener('input',function(){"
            + "  document.getElementById('amountHidden').value=this.value;"
            + "});"
            + "document.getElementById('orgSelect').addEventListener('change',function(){"
            + "  document.getElementById('orgIdHidden').value=this.value;"
            + "  updateOrgQR(this);"
            + "});"
            + "document.getElementById('needSelect').addEventListener('change',function(){"
            + "  document.getElementById('needIdHidden').value=this.value;"
            + "});"
            + "function updateOrgQR(sel){"
            + "  var qrSel=document.getElementById('qrSelect');"
            + "  if(sel.value && qrSel){"
            + "    for(var i=0;i<qrSel.options.length;i++){"
            + "      if(qrSel.options[i].value===sel.value){qrSel.selectedIndex=i;showQRPreview(qrSel);break;}"
            + "    }"
            + "  }"
            + "}"
            + buildQRDataScript()
            + "function showQRPreview(sel){"
            + "  var orgId=sel.value;"
            + "  var box=document.getElementById('qr-preview');"
            + "  var qrBox=document.getElementById('qr-box');"
            + "  var qrLabel=document.getElementById('qr-label');"
            + "  if(!orgId){box.style.display='none';return;}"
            + "  var qrData=window.qrDataMap[orgId];"
            + "  if(qrData){"
            + "    box.style.display='block';"
            + "    qrBox.innerHTML=generateQRSVG(qrData.code);"
            + "    qrLabel.textContent=qrData.label;"
            + "    document.getElementById('qrOrgIdHidden').value=orgId;"
            + "    document.getElementById('orgIdHidden').value=orgId;"
            + "    document.getElementById('orgSelect').value=orgId;"
            + "  }"
            + "}"
            + "function generateQRSVG(code){"
            + "  var size=160;"
            + "  var modules=21;"
            + "  var mod=Math.floor(size/modules);"
            + "  var hash=0;for(var i=0;i<code.length;i++){hash=(hash*31+code.charCodeAt(i))&0xFFFF;}"
            + "  var svg='<svg width=\"'+size+'\" height=\"'+size+'\" xmlns=\"http://www.w3.org/2000/svg\" style=\"border:2px solid #0ea5e9;border-radius:8px;background:#fff\">';"
            + "  for(var r=0;r<modules;r++){for(var c=0;c<modules;c++){"
            + "    var isEdge=(r<3&&c<3)||(r<3&&c>=modules-3)||(r>=modules-3&&c<3);"
            + "    var bit=(hash^(r*17+c*31))%7<3;"
            + "    if(bit||isEdge){svg+='<rect x=\"'+(c*mod)+'\" y=\"'+(r*mod)+'\" width=\"'+mod+'\" height=\"'+mod+'\" fill=\"#0a1628\"/>';}"
            + "  }}"
            + "  svg+='<rect x=\"'+mod+'\" y=\"'+mod+'\" width=\"'+(mod*2)+'\" height=\"'+(mod*2)+'\" fill=\"#fff\"/>';"
            + "  svg+='<rect x=\"'+(mod*(modules-4))+'\" y=\"'+mod+'\" width=\"'+(mod*2)+'\" height=\"'+(mod*2)+'\" fill=\"#fff\"/>';"
            + "  svg+='<rect x=\"'+mod+'\" y=\"'+(mod*(modules-4))+'\" width=\"'+(mod*2)+'\" height=\"'+(mod*2)+'\" fill=\"#fff\"/>';"
            + "  svg+='</svg>';"
            + "  return svg;"
            + "}"
            + "function formatCard(el){"
            + "  var v=el.value.replace(/\\D/g,'').substring(0,16);"
            + "  el.value=v.replace(/(\\d{4})(?=\\d)/g,'$1 ');"
            + "}"
            + "document.getElementById('upiInput').addEventListener('input',function(){"
            + "  var msg=document.getElementById('upi-valid-msg');"
            + "  var v=this.value.trim();"
            + "  if(v.indexOf('@')>0&&v.length>5){msg.textContent='✓ UPI format looks valid';msg.style.color='#10b981';}"
            + "  else{msg.textContent='';}"
            + "  document.getElementById('upiIdHidden').value=v;"
            + "});"
            + "function submitDonate(){"
            + "  var orgId=document.getElementById('orgIdHidden').value||document.getElementById('orgSelect').value;"
            + "  var amount=document.getElementById('amountHidden').value||document.getElementById('amtInput').value;"
            + "  var donor=document.getElementById('donorNameInput').value.trim();"
            + "  if(!orgId){alert('Please select an organization.');return;}"
            + "  if(!amount||parseFloat(amount)<10){alert('Please enter a valid donation amount (min ₹10).');return;}"
            + "  if(!donor){alert('Please enter your full name.');return;}"
            + "  document.getElementById('orgIdHidden').value=orgId;"
            + "  document.getElementById('amountHidden').value=amount;"
            + "  if(currentPayMethod==='qr'&&!document.getElementById('qrOrgIdHidden').value){"
            + "    alert('Please select a QR code from the list.');return;"
            + "  }"
            + "  document.getElementById('donateForm').submit();"
            + "}"
            + "// Pre-select if orgId in URL"
            + "var urlOrgId='" + (preSelectedOrgId != null ? preSelectedOrgId : "") + "';"
            + "if(urlOrgId){document.getElementById('orgSelect').value=urlOrgId;document.getElementById('orgIdHidden').value=urlOrgId;updateOrgQR(document.getElementById('orgSelect'));}"
            + "var urlNeedId='" + (preSelectedNeedId != null ? preSelectedNeedId : "") + "';"
            + "if(urlNeedId){document.getElementById('needSelect').value=urlNeedId;document.getElementById('needIdHidden').value=urlNeedId;}"
            + "</script>";
        Html.ok(ex, Html.layout("Donate", inner));
    }
 
    // Build QR data JS map for client-side QR display
    private String buildQRDataScript() {
        StringBuilder sb = new StringBuilder("window.qrDataMap={");
        s.orgs.values().stream()
            .filter(o -> o.demoQRCode != null && !o.demoQRCode.isBlank())
            .forEach(o -> sb.append("'").append(o.id).append("':{code:'")
                .append(esc(o.demoQRCode)).append("',label:'")
                .append(esc(o.demoQRLabel != null ? o.demoQRLabel : o.name))
                .append("'},"));
        sb.append("};");
        return sb.toString();
    }
 
    public void donateCheckout(HttpExchange ex) throws IOException {
        Map<String,String> f = readForm(ex);
        Double amount = parseDouble(f.get("amount"));
        String payMethod = f.getOrDefault("payMethod", "qr");
        String donorName = f.getOrDefault("donorName", "").trim();
        Long orgId = parseLong(f.getOrDefault("orgId", f.get("qrOrgId")));
        Long needId = parseLong(f.get("needId"));
        String message = f.getOrDefault("message", "");
 
        if (amount == null || amount < 10) {
            Html.ok(ex, Html.layout("Donate", "<section class='card'><p>⚠️ Invalid amount. Minimum donation is ₹10.</p><p><a class='btn' href='/donate'>← Back</a></p></section>")); return;
        }
        if (donorName.isBlank()) {
            Html.ok(ex, Html.layout("Donate", "<section class='card'><p>⚠️ Please enter your name.</p><p><a class='btn' href='/donate'>← Back</a></p></section>")); return;
        }
        if (orgId == null) {
            Html.ok(ex, Html.layout("Donate", "<section class='card'><p>⚠️ Please select an organization.</p><p><a class='btn' href='/donate'>← Back</a></p></section>")); return;
        }
 
        Organization org = s.orgs.get(orgId);
        String orgName = org != null ? org.name : "Organization";
 
        // Validate payment method specific fields
        if ("upi".equals(payMethod)) {
            String upiId = f.getOrDefault("upiId", "").trim();
            if (upiId.isBlank()) {
                Html.ok(ex, Html.layout("Donate", "<section class='card'><p>⚠️ Please enter a UPI ID.</p><p><a class='btn' href='/donate'>← Back</a></p></section>")); return;
            }
        } else if ("card".equals(payMethod)) {
            String cardNum = f.getOrDefault("cardNumber", "").replaceAll("\\s", "");
            if (cardNum.length() < 13) {
                Html.ok(ex, Html.layout("Donate", "<section class='card'><p>⚠️ Please enter a valid card number.</p><p><a class='btn' href='/donate'>← Back</a></p></section>")); return;
            }
        }
 
        // Record donation
        Donation d = new Donation();
        d.amount = amount; d.donorName = donorName; d.needId = needId;
        d.organizationName = orgName; d.paymentMethod = payMethod.toUpperCase();
        d.note = message.isBlank() ? "Donation to " + orgName : message;
        d.receiptNumber = "SC" + System.currentTimeMillis();
        d.fulfilled = true;
        s.addDonation(d);
 
        String txn = d.receiptNumber;
        try {
            Html.redirect(ex, "/receipt?amount=" + amount
                    + "&payee=" + URLEncoder.encode(orgName, "UTF-8")
                    + "&txn=" + URLEncoder.encode(txn, "UTF-8")
                    + "&donor=" + URLEncoder.encode(donorName, "UTF-8")
                    + "&method=" + URLEncoder.encode(payMethod.toUpperCase(), "UTF-8"));
        } catch (Exception e) {
            Html.redirect(ex, "/receipt?amount=" + amount + "&payee=Organization&txn=" + txn + "&donor=Donor&method=QR");
        }
    }
 
    // RECEIPT PAGE
    public void receiptPage(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex);
        Double amount = parseDouble(q.get("amount"));
        if (amount == null) amount = 0.0;
        String payee = q.getOrDefault("payee", "Organization");
        String txn = q.getOrDefault("txn", "SC" + System.currentTimeMillis());
        String donor = q.getOrDefault("donor", "Donor");
        String method = q.getOrDefault("method", "QR");
        Double taxBenefit = Donation.calculateTaxBenefit(amount);
 
        String inner = "<section class='card receipt-card' style='max-width:680px;margin:0 auto'>"
                + "<div class='receipt-success-banner'>"
                + "  <div style='font-size:56px;margin-bottom:8px'>🙏</div>"
                + "  <h2 style='margin:0;color:#fff;font-size:1.6rem'>Thank You for Your Donation!</h2>"
                + "  <p style='margin:8px 0 0 0;opacity:0.9'>Your generosity makes a real difference in people's lives.</p>"
                + "</div>"
                + "<div class='receipt-body'>"
                + "  <div class='receipt-header-row'>"
                + "    <span class='receipt-title'>DONATION RECEIPT</span>"
                + "    <span class='receipt-80g'>80G Eligible</span>"
                + "  </div>"
                + "  <div class='receipt-divider'>════════════════════════════</div>"
                + "  <div class='receipt-row'><span>Receipt No.</span><strong>" + esc(txn) + "</strong></div>"
                + "  <div class='receipt-row'><span>Date &amp; Time</span><strong id='receipt-dt'>—</strong></div>"
                + "  <div class='receipt-row'><span>Donor Name</span><strong>" + esc(donor) + "</strong></div>"
                + "  <div class='receipt-divider'>────────────────────────────</div>"
                + "  <div class='receipt-row'><span>Recipient Organization</span><strong>" + esc(payee) + "</strong></div>"
                + "  <div class='receipt-row'><span>Payment Mode</span><strong>📱 " + esc(method) + "</strong></div>"
                + "  <div class='receipt-row receipt-amount'><span>Donation Amount</span><strong style='color:#6366f1;font-size:1.3rem'>₹" + String.format("%.2f", amount) + "</strong></div>"
                + "  <div class='receipt-divider'>────────────────────────────</div>"
                + "  <div class='receipt-row tax-row'><span>📋 80G Deductible Amount (50%)</span><strong>₹" + String.format("%.2f", amount * 0.5) + "</strong></div>"
                + "  <div class='receipt-row tax-row-2'><span>💸 Est. Tax Saved @ 30% slab</span><strong>₹" + String.format("%.2f", taxBenefit) + "</strong></div>"
                + "  <div class='receipt-row tax-row-2'><span>💸 Est. Tax Saved @ 20% slab</span><strong>₹" + String.format("%.2f", amount * 0.5 * 0.20) + "</strong></div>"
                + "  <div class='receipt-divider'>────────────────────────────</div>"
                + "  <div class='receipt-row'><span>Status</span><strong style='color:#10b981'>✓ PAYMENT SUCCESSFUL</strong></div>"
                + "  <div class='receipt-divider'>════════════════════════════</div>"
                + "  <div class='receipt-note'>🇮🇳 <strong>Section 80G – Income Tax Act:</strong> 50% of your donation (₹" + String.format("%.2f", amount * 0.5) + ") is deductible from your taxable income. Use this receipt when filing your ITR. Deduction available under <strong>Old Tax Regime only</strong>. Keep this receipt for at least 6 years.</div>"
                + "</div>"
                + "<div class='receipt-actions'>"
                + "  <button onclick='window.print()' class='btn'>🖨 Print Receipt</button>"
                + "  <a href='/tax-benefits' class='btn'>💰 Tax Guide</a>"
                + "  <a href='/donate' class='btn primary'>💝 Donate Again</a>"
                + "  <a href='/' class='btn'>🏠 Home</a>"
                + "</div>"
                + "</section>"
                + "<script>document.getElementById('receipt-dt').textContent=new Date().toLocaleString('en-IN',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit'});</script>";
 
        Html.ok(ex, Html.layout("Donation Receipt", inner));
    }
 
    // PAY PROCESSING PAGE (animated)
    public void payProcessing(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex);
        String amountStr = q.getOrDefault("amount", "0");
        String payee = q.getOrDefault("payee", "");
        String txn = q.getOrDefault("txn", "");
        String donor = q.getOrDefault("donor", "");
        String method = q.getOrDefault("method", "QR");
        String redirectUrl = "/receipt?amount=" + amountStr
                + "&payee=" + URLEncoder.encode(payee, "UTF-8")
                + "&txn=" + URLEncoder.encode(txn, "UTF-8")
                + "&donor=" + URLEncoder.encode(donor, "UTF-8")
                + "&method=" + URLEncoder.encode(method, "UTF-8");
 
        String methodIcon = "QR".equals(method) ? "📱" : "UPI".equals(method) ? "💳" : "🏦";
        String methodLabel = "QR".equals(method) ? "QR Code Payment" : "UPI".equals(method) ? "UPI Transfer" : "Card Payment";
 
        String inner = "<style>"
            + ".payment-overlay{position:fixed;inset:0;background:linear-gradient(135deg,rgba(30,41,59,0.98),rgba(15,23,42,0.98));z-index:99999;display:flex;align-items:center;justify-content:center}"
            + ".payment-gateway{background:#fff;border-radius:20px;box-shadow:0 25px 60px -12px rgba(0,0,0,0.4);max-width:420px;width:95%;animation:slideUpGateway 0.5s cubic-bezier(0.23,1,0.32,1);overflow:hidden}"
            + ".gateway-header{background:linear-gradient(135deg,#6366f1,#8b5cf6);padding:28px;text-align:center;color:#fff}"
            + ".coin{width:90px;height:90px;margin:18px auto 6px;background:linear-gradient(135deg,#fbbf24,#f59e0b);border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:38px;animation:coinFlip 2s ease-in-out forwards;box-shadow:0 10px 30px rgba(245,158,11,0.4);perspective:600px}"
            + ".coin-label{text-align:center;font-size:12px;font-weight:700;color:#92400e;letter-spacing:0.5px;margin-bottom:6px;text-transform:uppercase;background:#fef3c7;border-radius:12px;padding:3px 10px;display:inline-block}"
            + ".method-badge{display:inline-flex;align-items:center;gap:6px;background:#ede9fe;color:#4f46e5;border-radius:20px;padding:4px 14px;font-size:12px;font-weight:700;margin:8px 0}"
            + ".gateway-body{padding:28px}"
            + ".payment-step{display:flex;align-items:center;gap:10px;margin:10px 0;font-size:13px;color:#475569}"
            + ".step-dot{width:8px;height:8px;border-radius:50%;background:#cbd5e1}"
            + ".step-dot.active{background:#6366f1;animation:dotPulse 1s infinite}"
            + ".confetti-particle{position:fixed;pointer-events:none}"
            + "@keyframes slideUpGateway{from{transform:translateY(40px);opacity:0}to{transform:translateY(0);opacity:1}}"
            + "@keyframes coinFlip{0%{transform:rotateY(0) scale(1)}25%{transform:rotateY(450deg) scale(1.2)}50%{transform:rotateY(900deg) scale(1)}75%{transform:rotateY(1350deg) scale(1.1)}to{transform:rotateY(1800deg) scale(1)}}"
            + "@keyframes dotPulse{0%,100%{transform:scale(1)}50%{transform:scale(1.5)}}"
            + "@keyframes confettiFall{to{transform:translateY(900px) rotateZ(720deg);opacity:0}}"
            + "</style>"
            + "<div class='payment-overlay'>"
            + "  <div class='payment-gateway'>"
            + "    <div class='gateway-header' id='gwHeader'>"
            + "      <h2 style='margin:0;font-size:1.4rem'>Processing Payment</h2>"
            + "      <p style='margin:6px 0 0;font-size:13px;opacity:0.9'>Please wait while we process your donation...</p>"
            + "    </div>"
            + "    <div class='gateway-body' style='text-align:center'>"
            + "      <div class='coin'>₹</div>"
            + "      <div style='text-align:center'><span class='coin-label'>🪙 Coin Flip — Sealing Your Kindness!</span></div>"
            + "      <div style='margin:8px 0'><span class='method-badge'>" + esc(methodIcon) + " " + esc(methodLabel) + "</span></div>"
            + "      <div style='text-align:center;margin:12px 0'>"
            + "        <div style='font-size:11px;color:#94a3b8;text-transform:uppercase;letter-spacing:0.8px'>Donation Amount</div>"
            + "        <div style='font-size:2.4rem;font-weight:800;background:linear-gradient(90deg,#6366f1,#8b5cf6);-webkit-background-clip:text;-webkit-text-fill-color:transparent'>₹" + esc(amountStr) + "</div>"
            + "      </div>"
            + "      <div id='steps' style='text-align:left'>"
            + "        <div class='payment-step'><div class='step-dot active' id='dot1'></div><span>Verifying payment details</span></div>"
            + "        <div class='payment-step'><div class='step-dot' id='dot2'></div><span>Processing transaction</span></div>"
            + "        <div class='payment-step'><div class='step-dot' id='dot3'></div><span>Confirming &amp; recording donation</span></div>"
            + "      </div>"
            + "      <p style='color:#94a3b8;font-size:12px;margin-top:16px;text-align:center'>To: <strong style='color:#0a1628'>" + esc(payee) + "</strong></p>"
            + "    </div>"
            + "  </div>"
            + "</div>"
            + "<div id='confetti'></div>"
            + "<script>"
            + "(function(){"
            + "  var s=1;"
            + "  function next(){if(s<=3){document.getElementById('dot'+s).classList.add('active');s++;if(s<=3)setTimeout(next,600);}}"
            + "  function success(){"
            + "    document.getElementById('gwHeader').style.background='linear-gradient(135deg,#10b981,#059669)';"
            + "    document.getElementById('gwHeader').innerHTML='<h2 style=\"margin:0;font-size:1.4rem\">✓ Payment Successful</h2><p style=\"margin:6px 0 0;font-size:13px;opacity:0.9\">Redirecting to your receipt...</p>';"
            + "    launchConfetti();"
            + "    setTimeout(function(){window.location.href='" + esc(redirectUrl) + "';},2500);"
            + "  }"
            + "  function launchConfetti(){"
            + "    var c=document.getElementById('confetti');"
            + "    var colors=['#ec4899','#f59e0b','#10b981','#3b82f6','#6366f1','#f43f5e'];"
            + "    for(var i=0;i<100;i++){"
            + "      var p=document.createElement('div');p.className='confetti-particle';"
            + "      var sz=Math.random()*8+4;"
            + "      p.style.cssText='width:'+sz+'px;height:'+sz+'px;background:'+colors[Math.floor(Math.random()*colors.length)]"
            + "        +';left:'+Math.random()*100+'%;top:'+(-Math.random()*20)+'%;border-radius:'+(Math.random()>0.5?'50%':'2px')"
            + "        +';animation:confettiFall '+(Math.random()*2+2)+'s ease-out '+(Math.random()*0.5)+'s forwards';"
            + "      c.appendChild(p);"
            + "    }"
            + "  }"
            + "  next();setTimeout(success,2200);"
            + "}());"
            + "</script>";
        Html.ok(ex, Html.layout("Processing Payment", inner));
    }
 
    // HEALTHCARE PAGE
    public void healthcare(HttpExchange ex) throws IOException {
        String facilitiesHtml = s.facilities.values().stream()
                .map(h -> {
                    Organization org = s.orgs.get(h.organizationId);
                    String orgName = org != null ? org.name : "Unknown";
                    return "<div class='card'>"
                        + "<h3>" + esc(nz(h.name)) + "</h3>"
                        + "<p><strong>Type:</strong> " + esc(nz(h.type)) + " | <strong>Org:</strong> " + esc(orgName) + "</p>"
                        + "<p><strong>Services:</strong> " + esc(nz(h.services)) + "</p>"
                        + "<p><strong>Capacity:</strong> " + (h.capacity != null ? h.capacity : "—") + " | <strong>Hours:</strong> " + esc(nz(h.availability)) + "</p>"
                        + (h.equipment != null ? "<p><strong>Equipment:</strong> " + esc(h.equipment) + "</p>" : "")
                        + "<p><strong>Contact:</strong> " + esc(nz(h.email)) + " • " + esc(nz(h.phone)) + "</p>"
                        + "</div>";
                }).collect(Collectors.joining());
 
 
        String inner = "<h2 class='page-title'>🏥 Healthcare Facilities</h2>"
                + "<p style='color:#475569;margin-bottom:24px'>Medical facilities and health services supporting orphanages and ashrams across Bengaluru.</p>"
                + "<section class='grid'>" + (facilitiesHtml.isBlank() ? "<p>No facilities listed yet.</p>" : facilitiesHtml) + "</section>";
        Html.ok(ex, Html.layout("Healthcare", inner));
    }
        public void healthCreate(HttpExchange ex) throws IOException {
        Map<String,String> f = readForm(ex);
        HealthcareFacility h = new HealthcareFacility();
        h.name = f.get("name"); h.type = f.get("type"); h.area = f.get("area"); h.address = f.get("address");
        h.phone = f.get("phone"); h.email = f.get("email"); h.services = f.get("services");
        h.capacity = parseInteger(f.get("capacity")); h.availability = f.get("availability");
        h.equipment = f.get("equipment"); h.operationalCost = parseDouble(f.get("operationalCost"));
        h.organizationId = parseLong(f.get("orgId"));
        if (nz(h.name).isBlank() || h.organizationId == null) { Html.redirect(ex, "/healthcare"); return; }
        s.addFacility(h);
        Html.redirect(ex, "/healthcare");
    }
    // PATIENTS PAGE
    public void patients(HttpExchange ex) throws IOException {
        String patientsHtml = s.patientRecords.values().stream()
                .map(p -> {
                    HealthcareFacility fac = s.facilities.get(p.facilityId);
                    String facName = fac != null ? fac.name : "Unknown";
                    Organization org = s.orgs.get(p.organizationId);
                    String orgName = org != null ? org.name : "Unknown";
                    String statusColor = "Active".equals(p.status) ? "#10b981" : "Discharged".equals(p.status) ? "#6366f1" : "#f59e0b";
                    return "<div class='card'>"
                        + "<h3>" + esc(nz(p.patientName)) + " <span style='font-size:13px;color:" + statusColor + ";font-weight:600'>[" + esc(nz(p.status)) + "]</span></h3>"
                        + "<p><strong>Age:</strong> " + (p.age != null ? p.age : "—") + " | <strong>Gender:</strong> " + esc(nz(p.gender)) + "</p>"
                        + "<p><strong>Condition:</strong> " + esc(nz(p.medicalCondition)) + "</p>"
                        + "<p><strong>Treatment:</strong> " + esc(nz(p.treatmentPlan)) + "</p>"
                        + "<p><strong>Facility:</strong> " + esc(facName) + " | <strong>Org:</strong> " + esc(orgName) + "</p>"
                        + (p.treatmentCost != null ? "<p><strong>Cost:</strong> ₹" + String.format("%.0f", p.treatmentCost)
                            + " | <strong>Paid:</strong> ₹" + String.format("%.0f", p.paidAmount != null ? p.paidAmount : 0) + "</p>" : "")
                        + (p.notes != null && !p.notes.isBlank() ? "<p style='font-size:13px;color:#64748b'>" + esc(p.notes) + "</p>" : "")
                        + "<a href='/donate?orgId=" + (p.organizationId != null ? p.organizationId : "") + "' class='btn primary' style='margin-top:8px'>💝 Support Treatment</a>"
                        + "</div>";
                }).collect(Collectors.joining());
 
        String inner = "<h2 class='page-title'>🧑‍⚕️ Patient Records</h2>"
                + "<p style='color:#475569;margin-bottom:24px'>Children and residents receiving medical care across our network facilities.</p>"
                + "<section class='grid'>" + (patientsHtml.isBlank() ? "<p style='padding:20px'>No patient records yet.</p>" : patientsHtml) + "</section>";
        Html.ok(ex, Html.layout("Patients", inner));
    }
 
    public void patientCreate(HttpExchange ex) throws IOException {
        Map<String,String> f = readForm(ex);
        PatientRecord p = new PatientRecord();
        p.patientName = f.get("patientName"); p.age = parseInteger(f.get("age")); p.gender = f.get("gender");
        p.facilityId = parseLong(f.get("facilityId")); p.medicalCondition = f.get("medicalCondition");
        p.treatmentPlan = f.get("treatmentPlan"); p.status = f.getOrDefault("status", "Active");
        p.treatmentCost = parseDouble(f.get("treatmentCost")); p.paidAmount = parseDouble(f.get("paidAmount"));
        p.notes = f.get("notes"); p.lastVisit = new Date();
        if (p.facilityId != null) {
            HealthcareFacility fac = s.facilities.get(p.facilityId);
            if (fac != null) p.organizationId = fac.organizationId;
        }
        if (nz(p.patientName).isBlank() || p.age == null || p.facilityId == null) { Html.redirect(ex, "/patients"); return; }
        s.addPatientRecord(p);
        Html.redirect(ex, "/patients");
    }
 
    public void patientDelete(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex);
        String idStr = q.get("id");
        if (idStr != null) { try { s.deletePatientRecord(Long.parseLong(idStr)); } catch (Exception ignored) {} }
        Html.redirect(ex, "/patients");
    }
 
    // TAX BENEFITS PAGE
    public void taxBenefits(HttpExchange ex) throws IOException {
        double totalDonated = s.donations.values().stream().mapToDouble(d -> d.amount == null ? 0.0 : d.amount).sum();
        double totalDeductible = totalDonated * 0.5;
        double totalTaxSaving = totalDonated * 0.5 * 0.30;
        int donorCount = s.donations.size();
        String inner = "<section class='card' style='max-width:900px;margin:0 auto'>"
                + "<h2 class='page-title'>💰 Tax Benefits for Donors</h2>"
                // Hero banner
                + "<div style='background:linear-gradient(135deg,#1b5e20,#2e7d32);padding:24px 28px;border-radius:14px;color:#fff;margin-bottom:28px;position:relative;overflow:hidden'>"
                + "<div style='font-size:40px;margin-bottom:8px'>🇮🇳 Section 80G – Income Tax Act</div>"
                + "<h3 style='margin:0 0 8px;font-size:1.3rem;color:#fff'>Your donations on Sahaara Connect qualify for tax deduction</h3>"
                + "<p style='margin:0;opacity:0.9;font-size:14px;line-height:1.7'>Every donation made through this platform is to registered charitable organizations under Section 80G of the Indian Income Tax Act. You are entitled to claim a <strong>50% deduction</strong> of your donation amount from your taxable income.</p>"
                + "</div>"
                // Stats row
                + "<div class='grid' style='grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:16px;margin-bottom:32px'>"
                + "<div class='stat-box'><div class='stat-num'>₹" + String.format("%.0f", totalDonated) + "</div><div class='stat-label'>Total Donated on Platform</div></div>"
                + "<div class='stat-box'><div class='stat-num'>₹" + String.format("%.0f", totalDeductible) + "</div><div class='stat-label'>Total 80G Deductible Amount</div></div>"
                + "<div class='stat-box'><div class='stat-num'>₹" + String.format("%.0f", totalTaxSaving) + "</div><div class='stat-label'>Est. Platform Tax Savings</div></div>"
                + "<div class='stat-box'><div class='stat-num'>" + donorCount + "</div><div class='stat-label'>Donations Eligible for 80G</div></div>"
                + "</div>"
                // What is Section 80G
                + "<div style='margin-bottom:24px'>"
                + "<h3 style='color:var(--primary);margin-bottom:12px'>📖 What is Section 80G?</h3>"
                + "<p style='color:#475569;line-height:1.8;font-size:14px'>Section 80G of the Income Tax Act, 1961 allows taxpayers in India to claim deductions on donations made to specified charitable institutions and relief funds. The government introduced this provision to encourage citizens to contribute toward social welfare. The deduction reduces your total taxable income, which means you pay less tax.</p>"
                + "</div>"
                // How it works
                + "<div style='margin-bottom:24px'>"
                + "<h3 style='color:var(--primary);margin-bottom:12px'>🧮 How Does Your Benefit Work?</h3>"
                + "<p style='color:#475569;line-height:1.8;font-size:14px;margin-bottom:12px'>Under Section 80G, donations to orphanages and social welfare ashrams qualify for a <strong>50% deduction</strong>. This means half of your donated amount is subtracted from your gross taxable income before calculating your tax liability.</p>"
                + "<div style='background:#f0f9ff;padding:20px 24px;border-radius:10px;border:1.5px solid #bae6fd;font-family:monospace;font-size:13px;line-height:2.2'>"
                + "<div>📌 <strong>Formula:</strong> Tax Saved = Donation × 50% × Your Tax Rate</div>"
                + "<div style='color:#0369a1;margin-top:8px'>Example 1: Donate ₹5,000 → Deduction = ₹2,500 → Tax saved (20% slab) = <strong>₹500</strong></div>"
                + "<div style='color:#065f46'>Example 2: Donate ₹10,000 → Deduction = ₹5,000 → Tax saved (30% slab) = <strong>₹1,500</strong></div>"
                + "<div style='color:#7c3aed'>Example 3: Donate ₹25,000 → Deduction = ₹12,500 → Tax saved (30% slab) = <strong>₹3,750</strong></div>"
                + "</div>"
                + "</div>"
                // Tax slabs
                + "<div style='margin-bottom:24px'>"
                + "<h3 style='color:var(--primary);margin-bottom:12px'>📊 Applicable Income Tax Slabs (Old Regime, FY 2024-25)</h3>"
                + "<table style='width:100%;border-collapse:collapse;font-size:13px'>"
                + "<tr style='background:#f8fafc;font-weight:700'><td style='padding:10px;border:1px solid #e2e8f0'>Annual Income</td><td style='padding:10px;border:1px solid #e2e8f0'>Tax Rate</td><td style='padding:10px;border:1px solid #e2e8f0'>Tax Saved on ₹10,000 Donation</td></tr>"
                + "<tr><td style='padding:10px;border:1px solid #e2e8f0'>Up to ₹2.5 lakh</td><td style='padding:10px;border:1px solid #e2e8f0'>Nil</td><td style='padding:10px;border:1px solid #e2e8f0'>₹0</td></tr>"
                + "<tr style='background:#f0fdf4'><td style='padding:10px;border:1px solid #e2e8f0'>₹2.5L – ₹5L</td><td style='padding:10px;border:1px solid #e2e8f0'>5%</td><td style='padding:10px;border:1px solid #e2e8f0;color:#059669;font-weight:700'>₹250</td></tr>"
                + "<tr><td style='padding:10px;border:1px solid #e2e8f0'>₹5L – ₹10L</td><td style='padding:10px;border:1px solid #e2e8f0'>20%</td><td style='padding:10px;border:1px solid #e2e8f0;color:#0369a1;font-weight:700'>₹1,000</td></tr>"
                + "<tr style='background:#f0fdf4'><td style='padding:10px;border:1px solid #e2e8f0'>Above ₹10L</td><td style='padding:10px;border:1px solid #e2e8f0'>30%</td><td style='padding:10px;border:1px solid #e2e8f0;color:#7c3aed;font-weight:700'>₹1,500</td></tr>"
                + "</table>"
                + "</div>"
                // How to claim
                + "<div style='margin-bottom:24px'>"
                + "<h3 style='color:var(--primary);margin-bottom:12px'>📋 How to Claim Your 80G Deduction</h3>"
                + "<ol style='color:#475569;line-height:2;font-size:14px;padding-left:20px'>"
                + "<li>Download and print your donation receipt from Sahaara Connect after donating.</li>"
                + "<li>Ensure the receipt mentions the organization's <strong>PAN number</strong> and <strong>80G registration number</strong>.</li>"
                + "<li>When filing your Income Tax Return (ITR), go to <strong>Section VI-A – Deductions</strong>.</li>"
                + "<li>Enter your total donated amount under <strong>80G (Donations to approved institutions)</strong>.</li>"
                + "<li>The 50% deductible amount will be automatically calculated and subtracted from your gross total income.</li>"
                + "<li>Keep all receipts safe for a minimum of <strong>6 years</strong> in case of assessment.</li>"
                + "</ol>"
                + "</div>"
                // Important note
                + "<div style='background:#fef3c7;padding:16px 20px;border-radius:10px;border-left:5px solid #f59e0b;margin-bottom:24px;font-size:13px;color:#92400e;line-height:1.7'>"
                + "<strong>⚠️ Important Notes:</strong><ul style='margin:8px 0 0 16px;padding:0'>"
                + "<li>The 50% deduction is subject to 10% of your Adjusted Gross Total Income (AGTI) as an upper limit for certain categories.</li>"
                + "<li>This deduction is available only under the <strong>Old Tax Regime</strong>. If you opt for the new regime, Section 80G deductions do not apply.</li>"
                + "<li>Cash donations above ₹2,000 are not eligible for deduction — all our platform donations via QR/UPI/Card are fully valid.</li>"
                + "<li>Consult a qualified Chartered Accountant or tax advisor for personalized advice.</li>"
                + "</ul>"
                + "</div>"
                + "<div style='text-align:center;margin-top:8px'>"
                + "<a href='/donate' class='btn primary' style='padding:14px 32px;font-size:15px'>💝 Donate &amp; Save Tax Now</a>&nbsp;&nbsp;"
                + "<a href='/' class='btn'>🏠 Home</a>"
                + "</div>"
                + "</section>";
        Html.ok(ex, Html.layout("Tax Benefits", inner));
    }
 
    // Static assets
    public static void serveCss(HttpExchange ex) throws IOException {
        Html.raw(ex, "text/css; charset=utf-8", Css.styles());
    }
    public static void serveJs(HttpExchange ex) throws IOException {
        Html.raw(ex, "application/javascript; charset=utf-8", Js.script());
    }
 
    // Helpers
    private static Map<String,String> parseQuery(HttpExchange ex) { return parseParams(ex.getRequestURI().getRawQuery()); }
    private static Map<String,String> readForm(HttpExchange ex) throws IOException {
        return parseParams(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }
    private static Map<String,String> parseParams(String raw) {
        Map<String,String> map = new HashMap<>();
        if (raw == null || raw.isBlank()) return map;
        for (String p : raw.split("&")) {
            int i = p.indexOf('=');
            if (i >= 0) map.put(urlDecode(p.substring(0,i)), urlDecode(p.substring(i+1)));
            else map.put(urlDecode(p), "");
        }
        return map;
    }
    private static String urlDecode(String s) { try { return URLDecoder.decode(s, StandardCharsets.UTF_8.name()); } catch (Exception e) { return s; } }
    private static Double parseDouble(String s) { try { return (s==null||s.isBlank()) ? null : Double.parseDouble(s); } catch (Exception e) { return null; } }
    private static Long parseLong(String s) { try { return (s==null||s.isBlank()) ? null : Long.parseLong(s); } catch (Exception e) { return null; } }
    private static Integer parseInteger(String s) { try { return (s==null||s.isBlank()) ? null : Integer.parseInt(s); } catch (Exception e) { return null; } }
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    private static String nz(String s) { return s == null ? "" : s; }
}
 
// ===== HTML Layout =====
class Html {
    public static String layout(String pageTitle, String inner) {
        return "<!doctype html><html lang='en'><head>"
            + "<meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
            + "<title>Sahaara Connect • " + Router.esc(pageTitle) + "</title>"
            + "<link rel='preconnect' href='https://fonts.googleapis.com'>"
            + "<link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>"
            + "<link href='https://fonts.googleapis.com/css2?family=Playfair+Display:wght@700;800&family=DM+Sans:wght@400;500;600;700&display=swap' rel='stylesheet'>"
            + "<link rel='stylesheet' href='/static/app.css'>"
            + "<script defer src='/static/app.js'></script>"
            + "</head><body>"
            + "<header class='nav'>"
            + "<div class='brand'><span class='brand-icon'>🤝</span><span class='brand-name'>Sahaara Connect</span></div>"
            + "<nav>"
            + "<a href='/' class='link'>Home</a>"
            + "<a href='/organizations' class='link'>Organizations</a>"
            + "<a href='/needs' class='link'>Needs</a>"
            + "<a href='/healthcare' class='link'>Healthcare</a>"
            + "<a href='/patients' class='link'>Patients</a>"
            + "<a href='/donate' class='link donate-link'>💝 Donate</a>"
            + "<a href='/tax-benefits' class='link'>Tax Benefits</a>"
            + "</nav>"
            + "</header>"
            + "<main class='container'>" + inner + "</main>"
            + "<footer class='footer'>© 2024 Sahaara Connect • Serving Bengaluru's Communities with Compassion</footer>"
            + "</body></html>";
    }
 
    public static void ok(HttpExchange ex, String html) throws IOException {
        byte[] b = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
    public static void raw(HttpExchange ex, String ct, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
    public static void redirect(HttpExchange ex, String to) throws IOException {
        ex.getResponseHeaders().set("Location", to);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }
}
 
// ===== CSS =====
class Css {
    public static String styles() {
        return ""
        + ":root{"
        + "--bg:#f5f0eb;"
        + "--bg2:#fffdf9;"
        + "--card:#ffffff;"
        + "--text:#1a1a1a;"
        + "--text2:#4a5568;"
        + "--primary:#c2693f;"   /* warm terracotta */
        + "--primary2:#e8874f;"
        + "--secondary:#3d7a5e;"  /* forest green */
        + "--secondary2:#4e9a77;"
        + "--accent:#8B5E3C;"
        + "--shadow:0 4px 24px rgba(0,0,0,0.08);"
        + "--radius:14px;"
        + "}"
        + "*{box-sizing:border-box;margin:0;padding:0}"
        + "html{scroll-behavior:smooth}"
        + "body{font-family:'DM Sans',sans-serif;color:var(--text);background:var(--bg);min-height:100vh}"
        // Splash
        + ".splash{position:fixed;inset:0;display:flex;align-items:center;justify-content:center;"
        + "background:linear-gradient(135deg,#c2693f,#3d7a5e,#8B5E3C);z-index:9999;transition:opacity 0.6s,transform 0.6s}"
        + ".splash.hidden{opacity:0;pointer-events:none;transform:scale(0.96)}"
        + ".splash-inner{text-align:center;color:#fff;padding:40px}"
        + ".splash-emblem{font-size:72px;margin-bottom:16px;animation:bounceIn 0.8s ease}"
        + ".splash-text{font-family:'Playfair Display',serif;font-size:2rem;font-weight:800;letter-spacing:0.5px}"
        + ".splash-sub{font-size:14px;margin-top:8px;opacity:0.85}"
        // Nav
        + ".nav{display:flex;justify-content:space-between;align-items:center;padding:14px 32px;"
        + "background:rgba(255,253,249,0.92);backdrop-filter:blur(12px);position:sticky;top:0;z-index:100;"
        + "box-shadow:0 2px 12px rgba(0,0,0,0.06);border-bottom:2px solid #f0e8df}"
        + ".brand{display:flex;align-items:center;gap:10px}"
        + ".brand-icon{font-size:28px}"
        + ".brand-name{font-family:'Playfair Display',serif;font-weight:800;font-size:1.3rem;color:var(--primary)}"
        + "nav{display:flex;gap:2px;flex-wrap:wrap}"
        + ".link{text-decoration:none;color:var(--text2);padding:8px 14px;border-radius:8px;font-weight:500;font-size:14px;transition:all 0.2s}"
        + ".link:hover{background:#f0ebe4;color:var(--primary)}"
        + ".donate-link{background:var(--primary);color:#fff!important;border-radius:20px;padding:8px 18px}"
        + ".donate-link:hover{background:var(--primary2)!important;transform:translateY(-1px)}"
        // Container
        + ".container{padding:32px 40px;max-width:1180px;margin:0 auto}"
        + ".page-title{font-family:'Playfair Display',serif;font-size:2rem;font-weight:800;color:var(--text);margin-bottom:24px}"
        // Hero
        + ".hero{text-align:center;padding:72px 20px 48px}"
        + ".title{font-family:'Playfair Display',serif;font-size:3rem;font-weight:800;line-height:1.15;color:var(--text);margin-bottom:16px}"
        + ".subtitle{font-size:1.1rem;color:var(--text2);max-width:600px;margin:0 auto;line-height:1.7}"
        + ".cta{display:flex;gap:12px;justify-content:center;margin-top:32px;flex-wrap:wrap}"
        // Stats row
        + ".stats-row{display:flex;gap:20px;justify-content:center;flex-wrap:wrap;margin:32px 0}"
        + ".stat-box{background:var(--card);border-radius:var(--radius);padding:20px 28px;text-align:center;box-shadow:var(--shadow);border:1px solid #ede8e0;min-width:140px}"
        + ".stat-num{font-family:'Playfair Display',serif;font-size:1.7rem;font-weight:800;color:var(--primary)}"
        + ".stat-label{font-size:12px;color:var(--text2);margin-top:4px;text-transform:uppercase;letter-spacing:0.5px}"
        // Cards & Grid
        + ".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:22px;margin-top:16px}"
        + ".card{background:var(--card);border:1px solid #ede8e0;border-radius:var(--radius);padding:26px;box-shadow:var(--shadow);transition:all 0.25s;animation:fadeIn 0.5s ease}"
        + ".card:hover{transform:translateY(-3px);box-shadow:0 12px 32px rgba(194,105,63,0.12);border-color:var(--primary)}"
        + ".card h3{font-family:'Playfair Display',serif;margin:0 0 12px 0;color:var(--text);font-size:1.15rem}"
        + ".card p{margin:8px 0;line-height:1.6;color:var(--text2);font-size:14px}"
        // Org cards
        + ".org-card{position:relative;padding-top:36px}"
        + ".org-cat-badge{position:absolute;top:16px;right:16px;padding:4px 10px;border-radius:12px;font-size:11px;font-weight:700;text-transform:uppercase}"
        + ".org-cat-badge.orphanage{background:#fef3c7;color:#92400e}"
        + ".org-cat-badge.ashram{background:#d1fae5;color:#065f46}"
        + ".meta-label{font-size:15px}"
        + ".website-link{color:var(--secondary);text-decoration:none;font-weight:600}"
        + ".website-link:hover{text-decoration:underline}"
        // Buttons
        + ".btn{padding:10px 20px;border-radius:9px;border:2px solid #ddd;background:#fff;cursor:pointer;"
        + "display:inline-block;text-decoration:none;color:var(--text);font-weight:600;font-size:13px;"
        + "transition:all 0.2s;font-family:'DM Sans',sans-serif}"
        + ".btn:hover{transform:translateY(-2px);border-color:var(--primary);color:var(--primary)}"
        + ".btn.primary{background:var(--primary);color:#fff;border-color:var(--primary)}"
        + ".btn.primary:hover{background:var(--primary2);border-color:var(--primary2)}"
        + ".btn.danger{background:#fee2e2;border-color:#fca5a5;color:#991b1b}"
        + ".btn.danger:hover{background:#fecaca}"
        // Tags
        + ".tag{display:inline-block;padding:2px 8px;border-radius:10px;font-size:11px;font-weight:700;"
        + "background:#f1f5f9;color:#475569;margin-right:4px}"
        + ".tag.health{background:#d1fae5;color:#065f46}"
        // Toolbar
        + ".toolbar{display:flex;gap:10px;flex-wrap:wrap;margin-bottom:24px;align-items:center}"
        + ".toolbar input,.toolbar select{padding:10px 14px;border:1.5px solid #dde4ec;border-radius:8px;"
        + "font-size:14px;background:#fff;font-family:'DM Sans',sans-serif}"
        + ".toolbar input:focus,.toolbar select:focus{outline:none;border-color:var(--primary)}"
        // Forms
        + "label{display:block;margin:14px 0 6px 0;font-weight:600;color:var(--text);font-size:14px}"
        + "input[type='text'],input[type='email'],input[type='number'],input[type='password'],textarea,select{"
        + "width:100%;padding:11px 14px;border:1.5px solid #dde4ec;border-radius:9px;"
        + "font-size:14px;font-family:'DM Sans',sans-serif;margin-bottom:4px;background:#fff;color:var(--text)}"
        + "input:focus,textarea:focus,select:focus{outline:none;border-color:var(--primary);box-shadow:0 0 0 3px rgba(194,105,63,0.1)}"
        + "textarea{resize:vertical;min-height:80px}"
        + ".form-row{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:4px}"
        // Quote
        + ".quote{margin:28px 0;padding:20px 26px;border-left:4px solid var(--primary);background:var(--card);"
        + "border-radius:0 10px 10px 0;font-style:italic;color:var(--text2);font-size:15px;line-height:1.7}"
        // Health sections
        + ".health-section{margin-top:32px;margin-bottom:24px}"
        + ".health-section-header{display:flex;align-items:flex-start;gap:16px;margin-bottom:16px;padding:16px 20px;"
        + "border-radius:10px;background:var(--card);border:1px solid #ede8e0}"
        + ".health-icon{font-size:32px;flex-shrink:0}"
        + ".facility-section .health-section-header{border-left:4px solid #3b82f6}"
        + ".patient-section .health-section-header{border-left:4px solid #ef4444}"
        + ".general-section .health-section-header{border-left:4px solid #10b981}"
        + ".health-section-header h3{font-family:'Playfair Display',serif;margin:0 0 4px 0;font-size:1.1rem}"
        + ".health-section-header p{margin:0;font-size:13px;color:var(--text2)}"
        // Need cards
        + ".need-card{}"
        + ".progress-wrap{margin:12px 0}"
        + ".progress-bar{background:#f1f5f9;border-radius:10px;height:8px;overflow:hidden}"
        + ".progress-fill{height:100%;background:linear-gradient(90deg,var(--primary),var(--secondary));border-radius:10px;transition:width 0.5s}"
        // Donate page
        + ".donate-container{display:flex;flex-direction:column;gap:24px;max-width:760px;margin:0 auto}"
        + ".donate-step{position:relative;padding-top:32px}"
        + ".step-num{position:absolute;top:-16px;left:24px;width:32px;height:32px;border-radius:50%;"
        + "background:var(--primary);color:#fff;font-weight:800;font-size:14px;"
        + "display:flex;align-items:center;justify-content:center}"
        + ".amount-shortcuts{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:10px}"
        + ".amt-btn{padding:6px 14px;border:1.5px solid var(--primary);background:#fff;color:var(--primary);"
        + "border-radius:20px;cursor:pointer;font-size:13px;font-weight:600;transition:all 0.2s}"
        + ".amt-btn:hover{background:var(--primary);color:#fff}"
        + ".payment-method-tabs{display:flex;gap:8px;margin-bottom:20px;flex-wrap:wrap}"
        + ".pay-tab{padding:10px 20px;border:2px solid #dde4ec;border-radius:8px;background:#fff;"
        + "cursor:pointer;font-size:14px;font-weight:600;transition:all 0.2s;font-family:'DM Sans',sans-serif}"
        + ".pay-tab.active{background:var(--primary);color:#fff;border-color:var(--primary)}"
        + ".pay-tab:hover:not(.active){border-color:var(--primary);color:var(--primary)}"
        + ".pay-panel{}"
        + ".qr-demo-box{display:inline-block;padding:12px;background:#fff;border-radius:10px;border:2px solid var(--primary)}"
        // Receipt
        + ".receipt-card{max-width:680px;margin:0 auto;padding:0;overflow:hidden}"
        + ".receipt-success-banner{background:linear-gradient(135deg,var(--secondary),#2d6b4e);"
        + "padding:32px;text-align:center;color:#fff}"
        + ".receipt-body{padding:28px;font-family:'DM Mono',monospace,sans-serif;font-size:13px}"
        + ".receipt-header-row{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}"
        + ".receipt-title{font-weight:800;font-size:14px;letter-spacing:1px}"
        + ".receipt-80g{background:#10b981;color:#fff;padding:3px 10px;border-radius:10px;font-size:11px;font-weight:700}"
        + ".receipt-divider{color:#94a3b8;font-size:11px;margin:10px 0;letter-spacing:1px}"
        + ".receipt-row{display:flex;justify-content:space-between;margin:8px 0;gap:12px}"
        + ".receipt-amount{font-size:15px;margin-top:12px}"
        + ".tax-row{background:#e8f5e9;padding:8px 10px;border-radius:6px;margin:4px 0}"
        + ".tax-row-2{background:#fff3cd;padding:8px 10px;border-radius:6px;margin:4px 0}"
        + ".receipt-note{margin-top:12px;font-size:11px;color:#0d47a1;background:#e3f2fd;padding:10px;border-radius:6px;line-height:1.6}"
        + ".receipt-actions{padding:20px 28px;display:flex;gap:10px;flex-wrap:wrap;border-top:1px solid #ede8e0}"
        // Donor list
        + ".donor-list{list-style:none}"
        + ".donor-list li{display:flex;justify-content:space-between;padding:8px 0;border-bottom:1px solid #f0ebe4}"
        + ".donor-name{font-weight:600}"
        + ".donor-amt{color:var(--primary);font-weight:700}"
        + ".feature-list{list-style:none}"
        + ".feature-list li{padding:8px 0;border-bottom:1px solid #f0ebe4;font-size:14px}"
        // Footer
        + ".footer{text-align:center;padding:28px;color:#94a3b8;border-top:1px solid #ede8e0;margin-top:60px;font-size:13px}"
        // Animations
        + "@keyframes bounceIn{0%{transform:scale(0.5);opacity:0}70%{transform:scale(1.1)}100%{transform:scale(1);opacity:1}}"
        + "@keyframes fadeIn{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:translateY(0)}}"
        + "@media(max-width:700px){.nav{flex-direction:column;gap:10px;padding:12px 16px}"
        + ".container{padding:20px 16px}.hero{padding:48px 16px 32px}.title{font-size:2rem}"
        + ".form-row{grid-template-columns:1fr}}"
        + "@media print{.nav,.footer,.receipt-actions{display:none}.container{padding:0}}"
        + "";
    }
}
 
// ===== JS =====
class Js {
    public static String script() {
        return "(function(){"
            + "var hide=function(){var s=document.getElementById('splash');if(!s)return;"
            + "s.classList.add('hidden');setTimeout(function(){if(s&&s.parentNode)s.parentNode.removeChild(s);},600);};"
            + "window.addEventListener('load',function(){setTimeout(hide,1600);});"
            + "document.addEventListener('click',function(e){if(e.target&&e.target.closest&&e.target.closest('.splash-inner'))hide();});"
            + "})();"
            + "document.addEventListener('scroll',function(){"
            + "var h=document.querySelector('.nav');"
            + "if(h)h.style.boxShadow=window.scrollY>20?'0 4px 20px rgba(0,0,0,0.1)':'0 2px 12px rgba(0,0,0,0.06)';"
            + "});";
    }
}