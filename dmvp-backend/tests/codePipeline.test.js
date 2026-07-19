'use strict';

const { computeStructuralHash, compareCodeStructure, getSupportedLanguages } = require('../src/utils/codePipeline');

describe('Code Pipeline (TDD v5 Phase 2 Step 2.5)', () => {
  describe('computeStructuralHash', () => {
    test('computes hash for JavaScript code', () => {
      const code = `
        function add(a, b) {
          return a + b;
        }
      `;
      const result = computeStructuralHash(code, 'javascript');
      expect(result).toHaveProperty('structuralHash');
      expect(result).toHaveProperty('language', 'javascript');
      expect(result).toHaveProperty('nodeCount');
      expect(result).toHaveProperty('functionCount');
      expect(result.structuralHash).toMatch(/^[0-9a-f]{64}$/);
    });
    
    test('computes hash for Python code', () => {
      const code = `
        def multiply(x, y):
            return x * y
      `;
      const result = computeStructuralHash(code, 'python');
      expect(result.structuralHash).toMatch(/^[0-9a-f]{64}$/);
      expect(result.language).toBe('python');
    });
    
    test('same structure different variable names = same hash', () => {
      const code1 = `function add(a, b) { return a + b; }`;
      const code2 = `function multiply(x, y) { return x * y; }`;
      
      const hash1 = computeStructuralHash(code1, 'javascript');
      const hash2 = computeStructuralHash(code2, 'javascript');
      
      // Structural hashes should match (same AST structure)
      expect(hash1.structuralHash).toBe(hash2.structuralHash);
    });
    
    test('throws error for unsupported language', () => {
      expect(() => {
        computeStructuralHash('code', 'unsupported');
      }).toThrow('Unsupported language');
    });
  });
  
  describe('compareCodeStructure', () => {
    test('identifies structurally similar code', () => {
      const code1 = `function add(a, b) { return a + b; }`;
      const code2 = `function multiply(x, y) { return x * y; }`;
      
      const result = compareCodeStructure(code1, code2, 'javascript');
      expect(result).toHaveProperty('similar');
      expect(result).toHaveProperty('similarityScore');
      expect(result.similar).toBe(true);
      expect(result.similarityScore).toBeGreaterThan(0.8);
    });
    
    test('identifies structurally different code', () => {
      const code1 = `function add(a, b) { return a + b; }`;
      const code2 = `
        class Calculator {
          constructor() {
            this.value = 0;
          }
          add(x) {
            this.value += x;
            return this;
          }
        }
      `;
      
      const result = compareCodeStructure(code1, code2, 'javascript');
      expect(result.similar).toBe(false);
      // Different structures should have lower similarity
      expect(result.structuralHash1).not.toBe(result.structuralHash2);
    });
    
    test('returns both structural hashes', () => {
      const code1 = `function test() { return 1; }`;
      const code2 = `function demo() { return 2; }`;
      
      const result = compareCodeStructure(code1, code2, 'javascript');
      expect(result).toHaveProperty('structuralHash1');
      expect(result).toHaveProperty('structuralHash2');
      expect(result.structuralHash1).toMatch(/^[0-9a-f]{64}$/);
      expect(result.structuralHash2).toMatch(/^[0-9a-f]{64}$/);
    });
  });
  
  describe('getSupportedLanguages', () => {
    test('returns array of supported languages', () => {
      const languages = getSupportedLanguages();
      expect(Array.isArray(languages)).toBe(true);
      expect(languages).toContain('javascript');
      expect(languages).toContain('python');
      expect(languages).toContain('typescript');
      expect(languages).toContain('java');
      expect(languages).toContain('cpp');
    });
  });
});
