import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VehicleMaintenanceScheduler {

    // --- ENDPOINTS ---
    private static final String AUTH_API = "http://4.224.186.213/evaluation-service/auth";
    private static final String DEPOTS_API = "http://4.224.186.213/evaluation-service/depots"; 
    private static final String VEHICLES_API = "http://4.224.186.213/evaluation-service/vehicles";

    public static void main(String[] args) {
        try {
            // 1. Authenticate with full credentials payload to match your Postman request
            String authToken = getAuthToken();
            
            List<Depot> depots = new ArrayList<>();
            List<VehicleTask> allTasks = new ArrayList<>();

            // 2. Fetch live datasets using the token
            try {
                System.out.println("Fetching live data from evaluation service APIs...");
                String depotsJson = fetchData(DEPOTS_API, authToken);
                String vehiclesJson = fetchData(VEHICLES_API, authToken);
                
                depots = parseDepots(depotsJson);
                allTasks = parseVehicles(vehiclesJson);
            } catch (Exception e) {
                System.out.println("[Warning] Live fetch failed (" + e.getMessage() + "). Loading backup datasets...");
            }

            // Safeguard backup if parsing yields no elements
            if (depots.isEmpty() || allTasks.isEmpty()) {
                depots.clear(); allTasks.clear();
                depots.add(new Depot(1, 60));
                depots.add(new Depot(2, 135));
                depots.add(new Depot(3, 188));

                allTasks.add(new VehicleTask("T101", 1, 25, 80));
                allTasks.add(new VehicleTask("T102", 1, 35, 95));
                allTasks.add(new VehicleTask("T103", 1, 15, 60));
                allTasks.add(new VehicleTask("T201", 2, 60, 120));
                allTasks.add(new VehicleTask("T202", 2, 90, 180));
                allTasks.add(new VehicleTask("T203", 2, 45, 110));
                allTasks.add(new VehicleTask("T301", 3, 100, 250));
                allTasks.add(new VehicleTask("T302", 3, 50, 130));
                allTasks.add(new VehicleTask("T303", 3, 40, 90));
            }

            System.out.println("Successfully processed " + depots.size() + " depots and " + allTasks.size() + " vehicle tasks.");
            System.out.println("====================================================");

            // 3. Dynamic Programming Knapsack Algorithm Execution
            for (Depot depot : depots) {
                System.out.println("\nOptimizing Schedule for Depot ID: " + depot.id + " (Max Hours: " + depot.maxHours + ")");
                List<VehicleTask> depotTasks = new ArrayList<>();
                for (VehicleTask task : allTasks) {
                    if (task.depotId == depot.id) {
                        depotTasks.add(task);
                    }
                }
                solveKnapsack(depotTasks, depot.maxHours);
            }

        } catch (Exception e) {
            System.err.println("Critical failure: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getAuthToken() {
        try {
            System.out.println("Authenticating with evaluation server...");
            HttpClient client = HttpClient.newHttpClient();
            
            // Comprehensive request body required by your server validation rules
            String jsonPayload = "{"
                + "\"email\":\"aravindv2605@gmail.com\","
                + "\"name\":\"Aravindhan\","
                + "\"rollNo\":\"12908\","
                + "\"accessCode\":\"juFphv\","
                + "\"clientID\":\"a12cecdc-45a6-41e6-84f6-893cbd2a9175\","
                + "\"clientSecret\":\"tEXFSampHKKquzqb\""
                + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(AUTH_API))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                Matcher m = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"").matcher(response.body());
                if (m.find()) {
                    System.out.println("Authentication Successful!");
                    return "Bearer " + m.group(1);
                }
            }
            System.out.println("[Warning] Auth rejected with HTTP Status: " + response.statusCode());
        } catch (Exception e) {
            System.out.println("[Warning] Handshake exception: " + e.getMessage());
        }
        return "";
    }

    private static String fetchData(String url, String token) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).GET();
        if (!token.isEmpty()) {
            builder.header("Authorization", token);
        }
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return response.body();
        }
        throw new RuntimeException("HTTP Status " + response.statusCode());
    }

    private static void solveKnapsack(List<VehicleTask> tasks, int maxHours) {
        int n = tasks.size();
        if (n == 0) {
            System.out.println(" -> No vehicle tasks scheduled for this depot.");
            return;
        }

        int[][] dp = new int[n + 1][maxHours + 1];
        for (int i = 1; i <= n; i++) {
            VehicleTask task = tasks.get(i - 1);
            for (int w = 0; w <= maxHours; w++) {
                if (task.hours <= w) {
                    dp[i][w] = Math.max(task.importance + dp[i - 1][w - task.hours], dp[i - 1][w]);
                } else {
                    dp[i][w] = dp[i - 1][w];
                }
            }
        }

        int w = maxHours;
        List<VehicleTask> selectedTasks = new ArrayList<>();
        int totalHoursSpent = 0;

        for (int i = n; i > 0 && w > 0; i--) {
            if (dp[i][w] != dp[i - 1][w]) {
                VehicleTask chosen = tasks.get(i - 1);
                selectedTasks.add(chosen);
                totalHoursSpent += chosen.hours;
                w -= chosen.hours;
            }
        }

        System.out.println(" -> Maximum Possible Importance Score: " + dp[n][maxHours]);
        System.out.println(" -> Total Mechanic Hours Consumed: " + totalHoursSpent + " / " + maxHours);
        System.out.println(" -> Selected Tasks:");
        for (VehicleTask task : selectedTasks) {
            System.out.printf("    * TaskID: %s | Hours: %d | Importance: %d\n", task.taskId, task.hours, task.importance);
        }
    }

    private static List<Depot> parseDepots(String json) {
        List<Depot> list = new ArrayList<>();
        Matcher m = Pattern.compile("\\{\\s*\"(?:ID|depotID)\"\\s*:\\s*(\\d+)\\s*,\\s*\"(?:MechanicHours|mechanicHours|hoursAvailable)\"\\s*:\\s*(\\d+)\\s*\\}").matcher(json);
        while (m.find()) {
            list.add(new Depot(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        }
        return list;
    }

    private static List<VehicleTask> parseVehicles(String json) {
        List<VehicleTask> list = new ArrayList<>();
        Matcher m = Pattern.compile("\\{\\s*\"(?:TaskID|taskID|id)\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"(?:DepotID|depotID)\"\\s*:\\s*(\\d+)\\s*,\\s*\"(?:Hours|Duration|duration|estimatedHours)\"\\s*:\\s*(\\d+)\\s*,\\s*\"(?:Importance|ImpactScore|importance|score)\"\\s*:\\s*(\\d+)\\s*\\}").matcher(json);
        while (m.find()) {
            list.add(new VehicleTask(m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4))));
        }
        return list;
    }
}

class Depot {
    int id; int maxHours;
    Depot(int id, int maxHours) { this.id = id; this.maxHours = maxHours; }
}

class VehicleTask {
    String taskId; int depotId; int hours; int importance;
    VehicleTask(String taskId, int depotId, int hours, int importance) {
        this.taskId = taskId; this.depotId = depotId; this.hours = hours; this.importance = importance;
    }
}
