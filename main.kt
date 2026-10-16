interface Reportable {
    fun submitReport(): String
}

abstract class Incident(
    val incidentId: Int,
    location: String,
    description: String
) {

    private var _location: String = location
    private var _description: String = description

    public val location: String
        get() = _location

    public val description: String
        get() = _description

    public fun updateLocation(newLocation: String) {
        if (newLocation.isNotBlank()) {
            _location = newLocation
        }
    }

    public fun updateDescription(newDescription: String) {
        if (newDescription.isNotBlank()) {
            _description = newDescription
        }
    }

    public abstract fun getIncidentType(): String

    // PUBLIC CONCRETE METHOD
    public fun displayDetails() {
        println("Incident ID: $incidentId")
        println("Location: $location")
        println("Description: $description")
        println("Incident Type: ${getIncidentType()}")
    }
}

class RoadAccident(
    incidentId: Int,
    location: String,
    description: String
) : Incident(incidentId, location, description) {

    override fun getIncidentType(): String {
        return "Road Accident"
    }
}

class RoadHazard(
    incidentId: Int,
    location: String,
    description: String
) : Incident(incidentId, location, description) {

    override fun getIncidentType(): String {
        return "Road Hazard"
    }
}

class Report(
    public val reportId: Int,
    public val reporterName: String,
    public val incident: Incident
) : Reportable {

    override fun submitReport(): String {
        return "Report $reportId submitted by $reporterName"
    }

    public fun displayReport() {
        println("===== ROAD INCIDENT REPORT =====")
        println("Report ID: $reportId")
        println("Reporter: $reporterName")
        println(submitReport())
        println()
        incident.displayDetails()
        println("================================")
    }
}

fun main() {

    // OBJECTS
    val accident = RoadAccident(
        101,
        "Nasugbu National Highway",
        "Motorcycle collision"
    )

    val hazard = RoadHazard(
        102,
        "Nasugbu Town Proper",
        "Road obstruction"
    )

    val report = Report(
        1001,
        "Juan Dela Cruz",
        accident
    )

    accident.displayDetails()

    println()

    hazard.displayDetails()

    println()

    report.displayReport()

    println()

    println("===== UPDATE INCIDENT =====")

    accident.updateLocation("Barangay Bucana, Nasugbu")
    accident.updateDescription("Vehicle collision reported")

    accident.displayDetails()
}
