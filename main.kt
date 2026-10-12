
abstract class Incident(
    val incidentId: Int,
    val location: String,
    val description: String
) {

    abstract fun getIncidentType(): String

    fun displayDetails() {
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
    val reportId: Int,
    val reporterName: String,
    val incident: Incident
) {

    fun displayReport() {
        println("===== ROAD INCIDENT REPORT =====")
        println("Report ID: $reportId")
        println("Reporter: $reporterName")
        println()
        incident.displayDetails()
    }
}

fun main() {

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

    accident.displayDetails()

    println()

    hazard.displayDetails()

    println()

    val report = Report(
        1001,
        "Juan Dela Cruz",
        accident
    )

    report.displayReport()
}
