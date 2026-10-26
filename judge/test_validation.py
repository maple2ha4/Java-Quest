import unittest
from server import validate, normalize

class ValidationTest(unittest.TestCase):
    def test_rejects_traversal_duplicate_and_size(self):
        for files in [[{'name':'../Main.java','content':''}], [{'name':'Main.java','content':''}]*2,
                      [{'name':'Main.java','content':'x'*32769}]]:
            with self.assertRaises(ValueError): validate({'files':files,'tests':[{'input':'','expected':''}]})
    def test_requires_main_and_tests(self):
        with self.assertRaises(ValueError): validate({'files':[{'name':'Other.java','content':''}],'tests':[]})
    def test_normalization_keeps_meaningful_whitespace(self):
        self.assertEqual(normalize('hello\r\n'), 'hello')
        self.assertNotEqual(normalize('hello world'),normalize('hello  world'))

if __name__=='__main__':unittest.main()
