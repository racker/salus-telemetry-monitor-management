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
    SELECT id
    FROM   monitors
    WHERE  tenant_id = :tenantId
  )
  AND monitors.id IN
    (
      SELECT id
      FROM monitor_label_selectors
      WHERE
      id IN
      (
        SELECT id
        FROM monitors
        WHERE tenant_id = :tenantId
      )
      AND monitors.id IN
      (
        SELECT id
        FROM monitor_label_selectors
        WHERE %s
        GROUP BY id
        HAVING COUNT(*) >= 1
      )
 ))
ORDER BY monitors.id