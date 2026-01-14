-- ============================================
-- Auto API Framework - Table Creation Scripts
-- Database: PostgreSQL
-- ============================================

-- ============================================
-- Table: auto_case_template
-- Description: 存储自动化测试用例模板
-- ============================================
CREATE TABLE IF NOT EXISTS auto_case_template (
    component VARCHAR(255) NOT NULL,
    "templateName" VARCHAR(255) NOT NULL,
    script JSONB,
    variables JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (component, "templateName")
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_auto_case_template_component ON auto_case_template(component);
CREATE INDEX IF NOT EXISTS idx_auto_case_template_name ON auto_case_template("templateName");

-- Add comments
COMMENT ON TABLE auto_case_template IS '自动化测试用例模板表';
COMMENT ON COLUMN auto_case_template.component IS '组件名称';
COMMENT ON COLUMN auto_case_template."templateName" IS '模板名称';
COMMENT ON COLUMN auto_case_template.script IS '测试脚本（JSONB格式）';
COMMENT ON COLUMN auto_case_template.variables IS '模板变量（JSONB格式）';

-- ============================================
-- Table: auto_endpoint_all
-- Description: 存储所有服务的API端点信息
-- ============================================
CREATE TABLE IF NOT EXISTS auto_endpoint_all (
    id SERIAL PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL,
    path VARCHAR(1000) NOT NULL,
    method VARCHAR(10) NOT NULL CHECK (method IN ('GET', 'POST', 'PUT', 'DELETE', 'PATCH', 'HEAD', 'OPTIONS')),
    active BOOLEAN DEFAULT TRUE,
    inactive_reason VARCHAR(500),
    is_coverage BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_auto_endpoint_all_service ON auto_endpoint_all(service_name);
CREATE INDEX IF NOT EXISTS idx_auto_endpoint_all_path_method ON auto_endpoint_all(path, method);
CREATE INDEX IF NOT EXISTS idx_auto_endpoint_all_active ON auto_endpoint_all(active);
CREATE INDEX IF NOT EXISTS idx_auto_endpoint_all_coverage ON auto_endpoint_all(is_coverage);

-- Create unique index for service_name + path + method
CREATE UNIQUE INDEX IF NOT EXISTS idx_auto_endpoint_all_unique ON auto_endpoint_all(service_name, path, method);

-- Add comments
COMMENT ON TABLE auto_endpoint_all IS '所有服务的API端点信息表';
COMMENT ON COLUMN auto_endpoint_all.service_name IS '服务名称';
COMMENT ON COLUMN auto_endpoint_all.path IS 'API路径';
COMMENT ON COLUMN auto_endpoint_all.method IS 'HTTP方法（GET/POST/PUT/DELETE等）';
COMMENT ON COLUMN auto_endpoint_all.active IS '是否激活';
COMMENT ON COLUMN auto_endpoint_all.inactive_reason IS '未激活原因';
COMMENT ON COLUMN auto_endpoint_all.is_coverage IS '是否纳入测试覆盖';

-- ============================================
-- Sample Data (Optional)
-- ============================================

-- Sample auto_case_template data
INSERT INTO auto_case_template (component, "templateName", script, variables)
VALUES
    ('order_svc', 'create_order_template', '[{"step": "call API", "action": "POST /api/orders"}]', '{"baseUrl": "http://localhost:8080", "timeout": 5000}')
ON CONFLICT (component, "templateName") DO NOTHING;

-- Sample auto_endpoint_all data
INSERT INTO auto_endpoint_all (service_name, path, method, active, is_coverage)
VALUES
    ('order_svc', '/api/orders', 'POST', true, true),
    ('order_svc', '/api/orders/{id}', 'GET', true, true),
    ('order_svc', '/api/orders/{id}', 'PUT', true, true),
    ('order_svc', '/api/orders/{id}', 'DELETE', true, false)
ON CONFLICT (service_name, path, method) DO NOTHING;

-- ============================================
-- Useful Queries for Reference
-- ============================================

-- Query 1: Get all templates for a component
-- SELECT * FROM auto_case_template WHERE component = 'order_svc';

-- Query 2: Get all active endpoints for a service
-- SELECT * FROM auto_endpoint_all WHERE service_name = 'order_svc' AND active = true;

-- Query 3: Get endpoints by method
-- SELECT * FROM auto_endpoint_all WHERE method = 'GET' AND active = true;

-- Query 4: Get template with variables
-- SELECT component, "templateName", script, variables FROM auto_case_template;
