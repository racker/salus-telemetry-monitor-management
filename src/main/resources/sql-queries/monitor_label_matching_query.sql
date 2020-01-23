SELECT            monitors.id
FROM              monitors
LEFT OUTER JOIN   monitor_label_selectors AS ml ON monitors.id = ml.monitor_id
where             monitors.label_selector_method = 'AND'
AND               monitors.tenant_id = :tenantId
AND               monitors.resource_id IS NULL
AND               (ml.monitor_id IS NULL OR monitors.id IN
         (
    SELECT first_ml.monitor_id
    FROM   monitor_label_selectors AS first_ml
    WHERE  monitors.id IN
        (
            SELECT   search_labels.monitor_id
            FROM   (
                SELECT   monitor_id,
                   count(*) AS count
                FROM     monitor_label_selectors
                GROUP BY monitor_id) AS total_labels
            JOIN
                (
                    SELECT   monitor_id,
                        count(*) AS count
                    FROM     monitor_label_selectors
                    WHERE    %s
                    GROUP BY monitor_id) AS search_labels
            WHERE    total_labels.monitor_id = search_labels.monitor_id
            AND      search_labels.count >= total_labels.count
            GROUP BY search_labels.monitor_id)))
ORDER BY monitors.id