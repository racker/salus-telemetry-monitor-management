SELECT   monitors.id
FROM     monitors
JOIN     monitor_label_selectors AS ml
where    monitors.id = ml.monitor_id
AND      monitors.label_selector_method = 'OR'
AND      monitors.id IN
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
      FROM monitor_label_selectors
      WHERE
      monitor_label_selectors.monitor_id IN
      (
        SELECT inner_monitors.id
        FROM monitors AS inner_monitors
        WHERE tenant_id = :tenantId
      )
      AND monitors.id IN
      (
        SELECT monitor_label_selectors.monitor_id
        FROM monitor_label_selectors
        WHERE %s
        GROUP BY monitor_label_selectors.monitor_id
        HAVING COUNT(*) >= 1
      )
 ))
ORDER BY monitors.id