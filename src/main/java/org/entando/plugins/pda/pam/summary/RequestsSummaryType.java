package org.entando.plugins.pda.pam.summary;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MONTHS;
import static java.time.temporal.ChronoUnit.YEARS;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.entando.plugins.pda.core.engine.Connection;
import org.entando.plugins.pda.core.model.summary.FrequencyEnum;
import org.entando.plugins.pda.core.model.summary.Summary;
import org.entando.plugins.pda.core.model.summary.SummaryType;
import org.entando.plugins.pda.pam.engine.KieEngine;
import org.entando.plugins.pda.pam.service.api.KieApiService;
import org.kie.server.api.model.definition.QueryDefinition;
import org.kie.server.client.QueryServicesClient;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequestsSummaryType implements SummaryType {

    public static final String PDA_TOTAL_PREFIX = "pda-total-";
    public static final String PDA_PERC_DAYS_PREFIX = "pda-perc-days-";
    public static final String PDA_PERC_MONTHS_PREFIX = "pda-perc-months-";
    public static final String PDA_PERC_YEARS_PREFIX = "pda-perc-years-";
    public static final String KIE_SERVER_PERSISTENCE_DS = "${org.kie.server.persistence.ds}";
    public static final String CUSTOM_TARGET = "CUSTOM";

    private final KieApiService kieApiService;

    @Override
    public Summary calculateSummary(Connection connection, FrequencyEnum frequency) {
        String totalQuery = "SELECT min(startdate) as first_date, max(startdate) as end_date, count(*) as total\n"
                + "FROM processinstanceinfo\n";
        QueryServicesClient queryClient = kieApiService.getQueryServicesClient(connection);
        String total = String.valueOf(getTotal(queryClient, frequency, totalQuery));
        double percentage = 0.0;
        if (frequency.equals(FrequencyEnum.DAILY)) {
            percentage = getPercentageDays(queryClient);
        } else if (frequency.equals(FrequencyEnum.MONTHLY)) {
            percentage = getPercentageMonths(queryClient);
        } else if (frequency.equals(FrequencyEnum.ANNUALLY)) {
            percentage = getPercentageYears(queryClient);
        }
        return Summary.builder()
                .title(getDescription())
                .totalLabel("Total requests")
                .total(total)
                .percentage(percentage)
                .build();
    }

    @Override
    public String getEngine() {
        return KieEngine.TYPE;
    }

    @Override
    public String getId() {
        return "requests";
    }

    @Override
    public String getDescription() {
        return "Requests";
    }

    private double getTotal(QueryServicesClient queryClient, FrequencyEnum frequency, String query) {
        String queryName = PDA_TOTAL_PREFIX + getId();
        QueryDefinition queryDefinition = QueryDefinition.builder()
                .name(queryName)
                .source(KIE_SERVER_PERSISTENCE_DS)
                .expression(query).target(CUSTOM_TARGET)
                .build();
        queryClient.replaceQuery(queryDefinition);

        List<List> result = queryClient
                .query(queryName, QueryServicesClient.QUERY_MAP_RAW, 0, -1, List.class);
        if (!result.isEmpty()) {
            LocalDate firstDate = new Date((Long) result.get(0).get(0)).toInstant().atZone(ZoneId.systemDefault())
                    .toLocalDate();
            LocalDate lastDate = new Date((Long) result.get(0).get(1)).toInstant().atZone(ZoneId.systemDefault())
                    .toLocalDate();
            Double totalRecords = (Double) result.get(0).get(2);
            if (frequency.equals(FrequencyEnum.DAILY)) {
                long days = DAYS.between(firstDate, lastDate);
                return days > 0 ? totalRecords / days : totalRecords;
            } else if (frequency.equals(FrequencyEnum.MONTHLY)) {
                long months = MONTHS.between(firstDate, lastDate);
                return months > 0 ? totalRecords / months : totalRecords;
            } else if (frequency.equals(FrequencyEnum.ANNUALLY)) {
                long years = YEARS.between(firstDate, lastDate);
                return years > 0 ? totalRecords / years : totalRecords;
            }
        }
        return 0.0;
    }

    private double getPercentageDays(QueryServicesClient queryClient) {
        String queryName = PDA_PERC_DAYS_PREFIX + getId();
        QueryDefinition queryDefinition = QueryDefinition.builder()
                .name(queryName)
                .source(KIE_SERVER_PERSISTENCE_DS)
                .expression(getQueryPercentageDays()).target(CUSTOM_TARGET)
                .build();
        queryClient.replaceQuery(queryDefinition);

        List<List> result = queryClient
                .query(queryName, QueryServicesClient.QUERY_MAP_RAW, 0, -1, List.class);

        if (result.isEmpty()) {
            return 0.0;
        }
        LocalDate lastDate = LocalDate
                .of(((Double) result.get(0).get(2)).intValue(), ((Double) result.get(0).get(1)).intValue(),
                        ((Double) result.get(0).get(0)).intValue());
        Double lastDateValue = (Double) result.get(0).get(3);
        LocalDate beforeLastDate = LocalDate
                .of(((Double) result.get(1).get(2)).intValue(), ((Double) result.get(1).get(1)).intValue(),
                        ((Double) result.get(1).get(0)).intValue());
        Double beforeLastDateValue = (Double) result.get(1).get(3);
        return calculatePercentageDays(lastDate, lastDateValue, beforeLastDate, beforeLastDateValue);
    }

    private double calculatePercentageDays(LocalDate lastDate, Double lastDateValue, LocalDate beforeLastDate,
            Double beforeLastDateValue) {
        LocalDate today = LocalDate.now();
        if (today.compareTo(lastDate) > 0) {
            if (today.minusDays(1).compareTo(lastDate) == 0 && lastDateValue > 0) {
                return -100;
            }
        } else if (today.compareTo(lastDate) == 0 && today.minusDays(1).compareTo(beforeLastDate) == 0
                && beforeLastDateValue > 0) {
            return (lastDateValue - beforeLastDateValue) / Math.min(beforeLastDateValue, lastDateValue) * 100;
        }
        return 0;
    }

    private String getQueryPercentageDays() {
        return "SELECT day(startdate) as day, month(startdate) as month, year(startdate) as year, count(*) as total\n"
                + "FROM processinstanceinfo\n"
                + "GROUP BY day, month, year\n"
                + "ORDER BY year DESC, month DESC, day DESC\n"
                + "LIMIT 2\n";
    }

    private double getPercentageMonths(QueryServicesClient queryClient) {
        String queryName = PDA_PERC_MONTHS_PREFIX + getId();
        QueryDefinition queryDefinition = QueryDefinition.builder()
                .name(queryName)
                .source(KIE_SERVER_PERSISTENCE_DS)
                .expression(getQueryPercentageMonths()).target(CUSTOM_TARGET)
                .build();
        queryClient.replaceQuery(queryDefinition);

        List<List> result = queryClient
                .query(queryName, QueryServicesClient.QUERY_MAP_RAW, 0, -1, List.class);

        if (result.isEmpty()) {
            return 0.0;
        }
        LocalDate lastDate = LocalDate
                .of(((Double) result.get(0).get(1)).intValue(), ((Double) result.get(0).get(0)).intValue(), 1);
        Double lastDateValue = (Double) result.get(0).get(2);
        LocalDate beforeLastDate = LocalDate
                .of(((Double) result.get(1).get(1)).intValue(), ((Double) result.get(1).get(0)).intValue(), 1);
        Double beforeLastDateValue = (Double) result.get(1).get(2);
        return calculatePercentageMonths(lastDate, lastDateValue, beforeLastDate, beforeLastDateValue);
    }

    private double calculatePercentageMonths(LocalDate lastDate, Double lastDateValue, LocalDate beforeLastDate,
            Double beforeLastDateValue) {
        LocalDate thisMonth = LocalDate.now().withDayOfMonth(1);
        if (thisMonth.compareTo(lastDate.withDayOfMonth(1)) > 0) {
            if (thisMonth.minusMonths(1).compareTo(lastDate.withDayOfMonth(1)) == 0 && lastDateValue > 0) {
                return -100;
            }
        } else if (thisMonth.compareTo(lastDate) == 0
                && thisMonth.minusMonths(1).compareTo(beforeLastDate.withDayOfMonth(1)) == 0
                && beforeLastDateValue > 0) {
            return (lastDateValue - beforeLastDateValue) / Math.min(beforeLastDateValue, lastDateValue) * 100;
        }
        return 0;
    }

    private String getQueryPercentageMonths() {
        return "SELECT month(startdate) as month, year(startdate) as year, count(*) as total FROM processinstanceinfo\n"
                + "GROUP BY month, year\n"
                + "ORDER BY year DESC, month DESC\n"
                + "LIMIT 2\n";
    }

    private double getPercentageYears(QueryServicesClient queryClient) {
        String queryName = PDA_PERC_YEARS_PREFIX + getId();
        QueryDefinition queryDefinition = QueryDefinition.builder()
                .name(queryName)
                .source(KIE_SERVER_PERSISTENCE_DS)
                .expression(getQueryPercentageYears()).target(CUSTOM_TARGET)
                .build();
        queryClient.replaceQuery(queryDefinition);

        List<List> result = queryClient
                .query(queryName, QueryServicesClient.QUERY_MAP_RAW, 0, -1, List.class);

        if (result.isEmpty()) {
            return 0.0;
        }
        int lastYear = ((Double) result.get(0).get(0)).intValue();
        Double lastYearValue = (Double) result.get(0).get(1);
        int beforeLastYear = ((Double) result.get(1).get(0)).intValue();
        Double beforeLastYearValue = (Double) result.get(1).get(1);
        return calculatePercentageYears(lastYear, lastYearValue, beforeLastYear, beforeLastYearValue);
    }

    private double calculatePercentageYears(int lastYear, Double lastYearValue, int beforeLastYear,
            Double beforeLastYearValue) {
        int thisYear = LocalDate.now().getYear();
        if (thisYear > lastYear) {
            if (thisYear - 1 == lastYear && lastYearValue > 0) {
                return -100;
            }
        } else if (thisYear == lastYear && thisYear - 1 == beforeLastYear && beforeLastYearValue > 0) {
            return (lastYearValue - beforeLastYearValue) / Math.min(beforeLastYearValue, lastYearValue) * 100;
        }
        return 0;
    }

    private String getQueryPercentageYears() {
        return "SELECT year(startdate) as year, count(*) as total FROM processinstanceinfo\n"
                + "GROUP BY year\n"
                + "ORDER BY year DESC\n"
                + "LIMIT 2\n";
    }
}
