-- V17's palette didn't actually match the spreadsheet closely enough: quality mixes pastel and solid chips, and
-- sentiment is consistently pastel throughout (see samples/img_1.png, img_2.png) -- V17 used solid/dark colors for
-- everything. This corrects both palettes; matched by exact (scale type, label, current color) so a club that
-- customized a color away from either the original or V17 default is left untouched.

-- Quality: also fixes Excepcional!, which V17 left unchanged from the very first (pre-V17) default.
UPDATE rating_options ro SET color = '#D9EAD3'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Excepcional!' AND ro.color = '#2E7D32';
UPDATE rating_options ro SET color = '#1C4587'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Muito bom' AND ro.color = '#283593';
UPDATE rating_options ro SET color = '#9FC5E8'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Bom' AND ro.color = '#1E88E5';
UPDATE rating_options ro SET color = '#A67C52'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Regular' AND ro.color = '#6D4C41';
UPDATE rating_options ro SET color = '#B45F06'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Ruim' AND ro.color = '#EF6C00';
UPDATE rating_options ro SET color = '#CC0000'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'QUALITY' AND ro.label = 'Horrível' AND ro.color = '#C62828';

UPDATE rating_options ro SET color = '#9FC5E8'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Adorei' AND ro.color = '#0288D1';
UPDATE rating_options ro SET color = '#B6D7A8'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Gostei!' AND ro.color = '#43A047';
UPDATE rating_options ro SET color = '#D9D9A3'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Ambivalente' AND ro.color = '#9E9D24';
UPDATE rating_options ro SET color = '#FFE599'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Indiferente' AND ro.color = '#F9A825';
UPDATE rating_options ro SET color = '#F9CB9C'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Desgostei' AND ro.color = '#EF6C00';
UPDATE rating_options ro SET color = '#EA9999'
    FROM rating_scales rs WHERE ro.scale_id = rs.id AND rs.type = 'SENTIMENT' AND ro.label = 'Detestei' AND ro.color = '#D32F2F';
