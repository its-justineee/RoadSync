
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

    val location: String
        get() = _location

    val description: String
        get() = _description

    fun updateLocation(newLocation: String) {
        if (newLocation.isNotBlank()) {
            _location = newLocation
        }
    }

    fun updateDescription(newDescription: String) {
        if (newDescription.isNotBlank()) {
            _description = newDescription
        }
    }

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
) : Reportable {

    override fun submitReport(): String {
        return "Report $reportId submitted by $reporterName"
    }

    fun displayReport() {
        println("===== ROAD INCIDENT REPORT =====")
        println("Report ID: $reportId")
        println("Reporter: $reporterName")
        println(submitReport())
        println()

        incident.displayDetails()

        println("================================")
    }
}

fun readNonEmpty(prompt: String): String {
    while (true) {
        print(prompt)
        val input = readlnOrNull()?.trim().orEmpty()

        if (input.isNotBlank()) {
            return input
        }

        println("Input cannot be empty. Try again.")
    }
}

fun submitNewReport(
    reports: MutableList<Report>,
    reportId: Int,
    incidentId: Int
) {
    println("\n===== SUBMIT INCIDENT REPORT =====")

    val reporterName = readNonEmpty("Enter your name: ")

    println("\nSelect Incident Type:")
    println("1. Road Accident")
    println("2. Road Hazard")

    var incidentType = ""

    while (incidentType.isEmpty()) {
        print("Enter choice: ")

        when (readlnOrNull()?.trim()) {
            "1" -> incidentType = "1"
            "2" -> incidentType = "2"
            else -> println("Invalid choice. Please select 1 or 2.")
        }
    }

    val location = readNonEmpty("Enter incident location: ")
    val description = readNonEmpty("Describe the incident: ")

    val incident: Incident = when (incidentType) {
        "1" -> RoadAccident(
            incidentId,
            location,
            description
        )

        else -> RoadHazard(
            incidentId,
            location,
            description
        )
    }

    val report = Report(
        reportId,
        reporterName,
        incident
    )

    reports.add(report)

    println("\n${report.submitReport()}")
    println("Thank you for using RoadSync!")
}

fun viewReports(reports: List<Report>) {
    println("\n===== ALL INCIDENT REPORTS =====")

    if (reports.isEmpty()) {
        println("No reports available.")
        return
    }

    for (report in reports) {
        report.displayReport()
        println()
    }
}

fun main() {

    val reports = mutableListOf<Report>()

    var nextReportId = 1001
    var nextIncidentId = 101

    while (true) {

        println("\n================================")
        println("             ROADSYNC")
        println("   ROAD INCIDENT REPORTING SYSTEM")
        println("================================")
        println("1. Submit a Report")
        println("2. View All Reports")
        println("3. Exit")
        println("================================")

        print("Enter your choice: ")

        when (readlnOrNull()?.trim()) {

            "1" -> {
                submitNewReport(
                    reports,
                    nextReportId,
                    nextIncidentId
                )

                nextReportId++
                nextIncidentId++
            }

            "2" -> {
                viewReports(reports)
            }

            "3" -> {
                println("Thank you for using RoadSync!")
                break
            }

            else -> {
                println("Invalid choice. Please try again.")
            }
        }
    }
}
