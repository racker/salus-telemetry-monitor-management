SELECT              monitors.id
FROM                monitors
LEFT OUTER JOIN     monitor_label_selectors AS ml ON monitors.id = ml.monitor_id
where               monitors.label_selector_method = 'OR'
AND                 monitors.tenant_id = :tenantId -- this should get us the information we want while also limiting to that tenant
AND                 (ml.monitor_id IS NULL OR monitors.id IN
(
  SELECT monitor_id
  FROM   monitor_label_selectors
  WHERE  monitors.id IN
  (
    SELECT first_monitors.id
    FROM   monitors AS first_monitors
    WHERE  tenant_id = :tenantId
  )
  AND monitors.id IN
  (
    SELECT monitor_label_selectors.monitor_id
    FROM   monitor_label_selectors
    WHERE  monitor_label_selectors.monitor_id IN
    (
      SELECT inner_monitors.id
      FROM   monitors AS inner_monitors
      WHERE  tenant_id = :tenantId
    )
    AND monitors.id IN
    (
      SELECT   monitor_label_selectors.monitor_id
      FROM     monitor_label_selectors
      WHERE    %s
      GROUP BY monitor_label_selectors.monitor_id
      HAVING COUNT(*) >= 1
    )
  )
))
ORDER BY monitors.id