-- Replaces the old shared green->red gradient with each scale's own palette, matching the original spreadsheet's
-- chip colors (see samples/img_1.png for quality, img_2.png for sentiment). Only touches rows that still hold the
-- exact old default for their (scale type, label, position) -- a club that already customized a color via
-- PATCH /clubs/{clubId}/rating-options/{optionId} keeps whatever it has, since the WHERE clause won't match it.
UPDATE rating_options ro SET color = '#283593'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Muito bom' AND ro.color = '#7CB342';
UPDATE rating_options ro SET color = '#1E88E5'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Bom' AND ro.color = '#C0CA33';
UPDATE rating_options ro SET color = '#6D4C41'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Regular' AND ro.color = '#FDD835';
UPDATE rating_options ro SET color = '#EF6C00'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Ruim' AND ro.color = '#FB8C00';
UPDATE rating_options ro SET color = '#C62828'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Horrível' AND ro.color = '#E53935';

UPDATE rating_options ro SET color = '#0288D1'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Adorei' AND ro.color = '#2E7D32';
UPDATE rating_options ro SET color = '#43A047'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Gostei!' AND ro.color = '#7CB342';
UPDATE rating_options ro SET color = '#9E9D24'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Ambivalente' AND ro.color = '#C0CA33';
UPDATE rating_options ro SET color = '#F9A825'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Indiferente' AND ro.color = '#FDD835';
UPDATE rating_options ro SET color = '#EF6C00'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Desgostei' AND ro.color = '#FB8C00';
UPDATE rating_options ro SET color = '#D32F2F'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Detestei' AND ro.color = '#E53935';
