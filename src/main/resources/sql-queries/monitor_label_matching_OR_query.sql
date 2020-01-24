SELECT              monitors.id
FROM                monitors
LEFT OUTER JOIN     monitor_label_selectors AS ml ON monitors.id = ml.monitor_id
WHERE               monitors.label_selector_method = 'OR'
AND                 monitors.tenant_id = :tenantId
AND                 monitors.resource_id IS NULL
AND                 (ml.monitor_id IS NULL OR monitors.id IN
(
      SELECT   monitor_label_selectors.monitor_id
      FROM     monitor_label_selectors
      WHERE    %s
      GROUP BY monitor_label_selectors.monitor_id
      HAVING COUNT(*) >= 1
  )
)
ORDER BY monitors.id